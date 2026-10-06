package api.aura.controller.mcas;

import api.aura.config.McasAccesoUsuario;
import api.aura.dto.mcas.*;
import api.aura.entity.Cliente;
import api.aura.entity.mcas.*;
import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.entity.tablas_maestras.TBGestoras;
import api.aura.repository.ClienteRepository;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasProductoComercialRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.repository.tablas_maestras.TBGestorasRepository;
import api.aura.service.mcas.McasContratoLocator;
import api.aura.service.mcas.McasLoteService;
import api.aura.service.mcas.McasMensajes;
import api.aura.service.mcas.McasProposalService;
import api.aura.service.mcas.McasResumenContratoService;
import api.aura.service.mcas.McasRetrocesionPolicyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mcas/propuestas")
public class McasPropuestasController {

    private static final Logger log = LoggerFactory.getLogger(McasPropuestasController.class);
    private final McasProposalService service;
    private final ClienteRepository clienteRepo;
    private final TBComercializadorasRepository comercializadoraRepo;
    private final TBGestorasRepository gestoraRepo;
    private final McasProductoComercialRepository productoRepo;
    private final McasRetrocesionPolicyService retrocesiones;
    private final McasReglaComisionRepository reglaRepo;
    private final McasConjuntoReglasRepository conjuntoRepo;
    private final McasResumenContratoService resumenes;
    private final McasLoteService lotes;
    private final McasContratoLocator localizador;
    private final ObjectMapper mapper;

    public McasPropuestasController(McasProposalService service, ClienteRepository clienteRepo,
                                    TBComercializadorasRepository comercializadoraRepo, TBGestorasRepository gestoraRepo,
                                    McasProductoComercialRepository productoRepo,
                                    McasRetrocesionPolicyService retrocesiones,
                                    McasReglaComisionRepository reglaRepo, McasConjuntoReglasRepository conjuntoRepo,
                                    McasResumenContratoService resumenes, McasLoteService lotes,
                                    McasContratoLocator localizador, ObjectMapper mapper) {
        this.service = service;
        this.clienteRepo = clienteRepo;
        this.comercializadoraRepo = comercializadoraRepo;
        this.gestoraRepo = gestoraRepo;
        this.productoRepo = productoRepo;
        this.retrocesiones = retrocesiones;
        this.reglaRepo = reglaRepo;
        this.conjuntoRepo = conjuntoRepo;
        this.resumenes = resumenes;
        this.lotes = lotes;
        this.localizador = localizador;
        this.mapper = mapper;
    }

    // ---- Operador (cualquier usuario identificado) ----

    /** Comisión del contrato para quien lo graba: sin traza, códigos ni usuarios. */
    @McasAccesoUsuario
    @GetMapping("/contrato/{idContrato}/resumen")
    public ResponseEntity<?> resumen(@PathVariable Integer idContrato) {
        try { return ResponseEntity.ok(resumenes.resumen(idContrato)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    // ---- Validador (administrador) ----

    /** Pendientes para el contador del menú. */
    @GetMapping("/recuento")
    public ResponseEntity<McasRecuentoDTO> recuento() {
        return ResponseEntity.ok(lotes.recuento());
    }

    /** Contratos por número exacto, que empiecen por la referencia o por id (máx. 10). */
    @GetMapping("/buscar-contrato")
    public ResponseEntity<List<McasContratoEncontradoDTO>> buscarContrato(@RequestParam(required = false) String ref) {
        return ResponseEntity.ok(localizador.buscar(ref));
    }

    /** Aprueba varias propuestas (máx. 200), cada una en su transacción: resultados parciales. */
    @PostMapping("/aprobar-lote")
    public ResponseEntity<?> aprobarLote(@RequestBody Map<String, List<Long>> body) {
        try { return ResponseEntity.ok(lotes.aprobarLote(body != null ? body.get("ids") : null)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    /** «No procede»: cierra la propuesta sin comisión con el motivo indicado. */
    @PostMapping("/{id}/descartar")
    public ResponseEntity<?> descartar(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        try { return ResponseEntity.ok(toResumen(service.descartar(id, body != null ? body.get("motivo") : null))); }
        catch (IllegalStateException | IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
        catch (ObjectOptimisticLockingFailureException e) {
            return ResponseEntity.status(409).body(Map.of("error", "Conflicto de concurrencia. Recarga y reintenta."));
        }
    }

    /** Lista todas — resumen sin campos grandes, con cliente, comercializadora, FEE y regla */
    @GetMapping
    public ResponseEntity<List<McasPropuestaDTO>> listar() {
        List<McasPropuesta> todas = service.listarTodas();
        return ResponseEntity.ok(toResumenes(todas, todas));
    }

    /** Lista por contrato */
    @GetMapping("/contrato/{idContrato}")
    public ResponseEntity<List<McasPropuestaDTO>> porContrato(@PathVariable Integer idContrato) {
        List<McasPropuesta> delContrato = service.listarPorContrato(idContrato);
        return ResponseEntity.ok(toResumenes(delContrato, delContrato));
    }

    /** Detalle completo con líneas y calendario */
    @GetMapping("/{id}")
    public ResponseEntity<?> detalle(@PathVariable Long id) {
        return service.obtener(id).map(p -> {
            McasPropuestaDTO dto = toDetalle(p);
            dto.setLineas(service.obtenerLineas(id).stream().map(this::toLinea).collect(Collectors.toList()));
            dto.setCalendario(service.obtenerCalendario(id).stream().map(this::toCalendario).collect(Collectors.toList()));
            return ResponseEntity.ok(dto);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/auditoria")
    public ResponseEntity<List<McasAuditoria>> auditoria(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenerAuditoria(id));
    }

    @GetMapping("/contrato/{idContrato}/auditoria")
    public ResponseEntity<List<McasAuditoria>> auditoriaPorContrato(@PathVariable Integer idContrato) {
        return ResponseEntity.ok(service.obtenerAuditoriaPorContrato(idContrato));
    }

    /** Cálculo idempotente */
    @PostMapping("/calcular/{idContrato}")
    public ResponseEntity<?> calcular(@PathVariable Integer idContrato) {
        try { return ResponseEntity.ok(toResumen(service.iniciarCalculo(idContrato))); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
        catch (Exception e) {
            log.error("Error calcular MCAS contrato={}", idContrato, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error interno"));
        }
    }

    /** Fuerza nuevo cálculo (supera propuestas activas) */
    @PostMapping("/calcular-forzado/{idContrato}")
    public ResponseEntity<?> calcularForzado(@PathVariable Integer idContrato) {
        try { return ResponseEntity.ok(toResumen(service.triggerDesdeContrato(idContrato))); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
        catch (Exception e) {
            log.error("Error calcular-forzado MCAS contrato={}", idContrato, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error interno"));
        }
    }

    @PostMapping("/{id}/recalcular")
    public ResponseEntity<?> recalcular(@PathVariable Long id) {
        try { return ResponseEntity.ok(toResumen(service.recalcular(id))); }
        catch (IllegalStateException | IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<?> aprobar(@PathVariable Long id) {
        try { return ResponseEntity.ok(toResumen(service.aprobar(id))); }
        catch (IllegalStateException | IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
        catch (ObjectOptimisticLockingFailureException e) {
            return ResponseEntity.status(409).body(Map.of("error", "Conflicto de concurrencia. Recarga y reintenta."));
        }
    }

    /** Rechaza y abre la edición en el mismo paso: devuelve la propuesta en EN_EDICION. */
    @PostMapping("/{id}/rechazar")
    public ResponseEntity<?> rechazar(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try { return ResponseEntity.ok(toResumen(service.rechazar(id, body.get("motivo")))); }
        catch (IllegalStateException | IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    /** Abre la edición de una propuesta rechazada o que el motor no pudo calcular. */
    @PostMapping("/{id}/editar")
    public ResponseEntity<?> abrirEdicion(@PathVariable Long id) {
        try { return ResponseEntity.ok(toResumen(service.abrirEdicion(id))); }
        catch (IllegalStateException | IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/{id}/finalizar-editado")
    public ResponseEntity<?> finalizarEditado(@PathVariable Long id, @RequestBody McasEditarRequestDTO req) {
        try {
            return ResponseEntity.ok(toResumen(service.finalizarEditado(
                    id, req.getImporteDefinitivo(), req.getNumeroPagos(), req.getOverrideJson(), req.getMotivo())));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ObjectOptimisticLockingFailureException e) {
            return ResponseEntity.status(409).body(Map.of("error", "Conflicto de concurrencia. Recarga y reintenta."));
        }
    }

    // ---- Mappers ----

    /** Una sola propuesta: las demás del contrato dicen si ya tiene comisión vigente. */
    private McasPropuestaDTO toResumen(McasPropuesta p) {
        return toResumenes(List.of(p), service.listarPorContrato(p.getIdContrato())).get(0);
    }

    /**
     * Resumen de varias propuestas. Instantánea y traza se leen una vez por
     * propuesta y los nombres se buscan por lotes (un findAllById por catálogo y
     * una sola consulta de retrocesiones), sin N+1. Un JSON ilegible deja esos
     * campos vacíos pero no rompe el listado.
     *
     * <p>{@code contexto} son las propuestas entre las que se busca si el contrato
     * ya tiene una comisión vigente (el listado completo, o las del contrato): en
     * ese caso no se ofrece «Completar manualmente», que se rechazaría.</p>
     */
    private List<McasPropuestaDTO> toResumenes(List<McasPropuesta> propuestas, List<McasPropuesta> contexto) {
        List<Datos> datos = propuestas.stream().map(this::leer).collect(Collectors.toList());
        Map<Integer, String> clientes = nombres(datos, Datos::idCliente, clienteRepo::findAllById, Cliente::getId, Cliente::getNombre);
        Map<Integer, String> comercializadoras = nombres(datos, Datos::idComercializadora,
                comercializadoraRepo::findAllById, TBComercializadoras::getId, TBComercializadoras::getNombre);
        Map<Integer, String> gestoras = nombres(datos, Datos::idGestora,
                gestoraRepo::findAllById, TBGestoras::getId, TBGestoras::getNombre);
        Map<Integer, String> productos = nombres(datos, Datos::idProducto,
                productoRepo::findAllById, McasProductoComercial::getId, McasProductoComercial::getNombre);
        Map<Integer, String> nombresRegla = nombres(datos, Datos::idRegla,
                reglaRepo::findAllById, McasReglaComision::getId, McasReglaComision::getNombre);
        Map<Integer, String> nombresConjunto = nombres(datos, d -> d.propuesta().getIdConjuntoReglas(),
                conjuntoRepo::findAllById, McasConjuntoReglas::getId, McasConjuntoReglas::getNombre);
        Map<Integer, String> retro = retrocesiones.describirTodas(propuestas.stream()
                .map(McasPropuesta::getIdPoliticaRetrocesion).filter(Objects::nonNull).collect(Collectors.toSet()));
        Set<String> conComision = service.contratosConComisionVigente(contexto);
        Map<Integer, Long> ultimas = ultimaPorContrato(contexto);

        List<McasPropuestaDTO> resultado = new ArrayList<>();
        for (Datos d : datos) {
            McasPropuesta p = d.propuesta();
            McasPropuestaDTO dto = new McasPropuestaDTO();
            dto.setId(p.getId()); dto.setIdContrato(p.getIdContrato()); dto.setTipoEvento(p.getTipoEvento());
            dto.setEstado(p.getEstado()); dto.setIdConjuntoReglas(p.getIdConjuntoReglas());
            dto.setIdPoliticaRetrocesion(p.getIdPoliticaRetrocesion()); dto.setHashContrato(p.getHashContrato());
            dto.setImporteAutomatico(p.getImporteCalculado()); // importeAutomatico en DTO = importe_calculado en DB
            dto.setImporteDefinitivo(p.getImporteDefinitivo());
            dto.setNumeroPagos(p.getNumeroPagos()); dto.setMotivoRechazo(p.getMotivoRechazo());
            dto.setAdvertenciasJson(p.getAdvertenciasJson()); dto.setIdServicioCreado(p.getIdServicioCreado());
            dto.setAdvertencias(advertencias(p));
            dto.setCreadoPor(p.getCreadoPor()); dto.setCreadoEn(p.getCreadoEn());
            dto.setAprobadoPor(p.getAprobadoPor()); dto.setAprobadoEn(p.getAprobadoEn());
            dto.setActualizadoPor(p.getActualizadoPor()); dto.setActualizadoEn(p.getActualizadoEn());
            dto.setVersionOptimista(p.getVersionOptimista());
            dto.setAdmiteEdicionManual(McasProposalService.ESTADOS_EDITABLES.contains(p.getEstado())
                    && !conComision.contains(McasProposalService.claveComision(p.getIdContrato(), p.getTipoEvento())));
            dto.setEsRenovacion("RENOVACION".equals(p.getTipoEvento()));
            dto.setUltimaDelContrato(Objects.equals(ultimas.get(p.getIdContrato()), p.getId()));

            JsonNode inst = d.instantanea();
            dto.setNumeroContrato(texto(inst, "numero_contrato"));
            dto.setIdCliente(d.idCliente()); dto.setNombreCliente(nombre(clientes, d.idCliente()));
            String cups = texto(inst, "cups_luz");
            dto.setCups(cups != null && !cups.isBlank() ? cups : texto(inst, "cups_gas"));
            dto.setIdComercializadora(d.idComercializadora());
            dto.setNombreComercializadora(nombre(comercializadoras, d.idComercializadora()));
            dto.setIdGestora(d.idGestora()); dto.setNombreGestora(nombre(gestoras, d.idGestora()));
            dto.setTipoPrecio(texto(inst, "tipo_precio"));
            dto.setTipoSuministro(texto(inst, "tipo_suministro"));
            dto.setSegmento(texto(inst, "segmento"));
            dto.setProductoComercial(nombre(productos, d.idProducto()));
            dto.setConsumoAnualKwh(decimal(inst, "consumo_anual_kwh"));
            dto.setFeeEurMwh(decimal(inst, "fee"));

            JsonNode traza = d.traza();
            String tarifa = texto(traza, "tarifa_acceso");
            dto.setTarifaAcceso(tarifa != null ? tarifa : texto(d.normalizados(), "tarifa_acceso"));
            dto.setPotenciaCalculoKw(decimal(traza, "potencia_calculo_kw"));
            dto.setPotenciaOrigen(texto(traza, "potencia_origen"));
            dto.setPorcentajeAplicacionFee(decimal(traza, "porcentaje_aplicacion_fee"));
            dto.setCodigoRegla(texto(traza, "regla"));
            // Nombre actual de la regla y del conjunto; si la regla ya no existe, el de la traza.
            String nombreRegla = sinBlancos(nombre(nombresRegla, d.idRegla()));
            dto.setNombreRegla(nombreRegla != null ? nombreRegla : sinBlancos(texto(traza, "regla_nombre")));
            dto.setNombreConjunto(sinBlancos(nombre(nombresConjunto, p.getIdConjuntoReglas())));
            dto.setMetodoCalculo(texto(traza, "metodo"));
            dto.setCodigoConjunto(texto(traza, "conjunto"));
            dto.setVersionConjunto(texto(traza, "conjunto_version"));
            dto.setRetrocesionPotencial(nombre(retro, p.getIdPoliticaRetrocesion()));
            resultado.add(dto);
        }
        return resultado;
    }

    private McasPropuestaDTO toDetalle(McasPropuesta p) {
        McasPropuestaDTO d = toResumen(p);
        d.setTrazaCalculoJson(p.getTrazaCalculoJson());
        d.setInstantaneaJson(p.getInstantaneaJson());
        d.setDatosNormalizadosJson(p.getDatosNormalizadosJson());
        d.setDatosEditadosJson(p.getDatosEditadosJson());
        d.setFechaPrimeraAplicacion(p.getFechaPrimeraAplicacion());
        return d;
    }

    /** JSON de la propuesta ya leído, con los ids que hay que resolver a nombre. */
    private record Datos(McasPropuesta propuesta, JsonNode instantanea, JsonNode traza, JsonNode normalizados,
                         Integer idCliente, Integer idComercializadora, Integer idGestora, Integer idProducto,
                         Integer idRegla) {}

    private Datos leer(McasPropuesta p) {
        JsonNode inst = json(p, p.getInstantaneaJson(), "instantanea");
        JsonNode traza = json(p, p.getTrazaCalculoJson(), "traza");
        return new Datos(p, inst, traza,
                json(p, p.getDatosNormalizadosJson(), "datos normalizados"),
                entero(inst, "cliente_id"), entero(inst, "comercializadora_id"),
                entero(inst, "gestora_id"), entero(inst, "producto_comercial_id"), entero(traza, "regla_id"));
    }

    /**
     * Advertencias para el usuario. Las propuestas antiguas guardaban la causa
     * técnica («MCAS_ENABLED=false»...): se traducen al leerlas; las nuevas ya
     * vienen en texto humano y no cambian.
     */
    private List<String> advertencias(McasPropuesta p) {
        if (p.getAdvertenciasJson() == null || p.getAdvertenciasJson().isBlank()) return List.of();
        try {
            List<String> guardadas = mapper.readValue(p.getAdvertenciasJson(), new TypeReference<List<String>>() {});
            return guardadas.stream().filter(Objects::nonNull).map(McasMensajes::paraOperador).toList();
        } catch (Exception e) {
            log.warn("MCAS propuesta={} advertencias ilegibles: {}", p.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Id de la propuesta más reciente de cada contrato entre las dadas (mayor
     * creadoEn; a igualdad, mayor id), en memoria y sin consultas. Mismo criterio
     * que el recuento: una DESACTUALIZADO que no es la última ya está sustituida.
     */
    private Map<Integer, Long> ultimaPorContrato(Collection<McasPropuesta> propuestas) {
        Comparator<McasPropuesta> reciente = Comparator
                .comparing(McasPropuesta::getCreadoEn, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(McasPropuesta::getId, Comparator.nullsFirst(Comparator.naturalOrder()));
        Map<Integer, McasPropuesta> ultimas = new HashMap<>();
        for (McasPropuesta p : propuestas)
            ultimas.merge(p.getIdContrato(), p, (a, b) -> reciente.compare(a, b) >= 0 ? a : b);
        Map<Integer, Long> ids = new HashMap<>();
        ultimas.forEach((contrato, p) -> ids.put(contrato, p.getId()));
        return ids;
    }

    private String sinBlancos(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private JsonNode json(McasPropuesta p, String texto, String campo) {
        if (texto == null || texto.isBlank()) return MissingNode.getInstance();
        try {
            return mapper.readTree(texto);
        } catch (Exception e) {
            log.warn("MCAS propuesta={} {} ilegible: {}", p.getId(), campo, e.getMessage());
            return MissingNode.getInstance();
        }
    }

    /** Un findAllById por catálogo con los ids de todas las propuestas. */
    private <E> Map<Integer, String> nombres(List<Datos> datos, Function<Datos, Integer> id,
                                             Function<Set<Integer>, List<E>> buscar,
                                             Function<E, Integer> idEntidad, Function<E, String> nombre) {
        Set<Integer> ids = datos.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        Map<Integer, String> mapa = new HashMap<>();
        for (E e : buscar.apply(ids))
            if (nombre.apply(e) != null) mapa.put(idEntidad.apply(e), nombre.apply(e));
        return mapa;
    }

    /** Busca por id admitiendo id nulo (Map.of() no lo admite). */
    private String nombre(Map<Integer, String> nombres, Integer id) {
        return id == null ? null : nombres.get(id);
    }

    /** Texto de un campo; null si falta, es nulo o es el texto «null» de datos_normalizados_json. */
    private String texto(JsonNode nodo, String campo) {
        JsonNode v = nodo.path(campo);
        if (v.isMissingNode() || v.isNull()) return null;
        String t = v.asText();
        return "null".equals(t) ? null : t;
    }

    private Integer entero(JsonNode nodo, String campo) {
        JsonNode v = nodo.path(campo);
        return v.canConvertToInt() ? v.asInt() : null;
    }

    private BigDecimal decimal(JsonNode nodo, String campo) {
        JsonNode v = nodo.path(campo);
        if (v.isNumber()) return v.decimalValue();
        String t = texto(nodo, campo);
        if (t == null || t.isBlank()) return null;
        try { return new BigDecimal(t); } catch (NumberFormatException e) { return null; }
    }

    private McasLineaDTO toLinea(McasLineaPropuesta l) {
        McasLineaDTO d = new McasLineaDTO();
        d.setId(l.getId()); d.setTipoLinea(l.getTipoLinea()); d.setDescripcion(l.getDescripcion());
        d.setMetodo(l.getMetodo()); d.setValorBase(l.getValorBase()); d.setValorTasa(l.getValorTasa());
        d.setValorPorcentaje(l.getValorPorcentaje());
        d.setImporteAutomatico(l.getImporteCalculado()); // DTO: importeAutomatico = DB: importe_calculado
        d.setImporteDefinitivo(l.getImporteDefinitivo()); d.setIdRegla(l.getIdRegla()); d.setOrden(l.getOrden());
        return d;
    }

    private McasCalendarioDTO toCalendario(McasCalendarioPago c) {
        McasCalendarioDTO d = new McasCalendarioDTO();
        d.setId(c.getId()); d.setNumeroPago(c.getNumeroPago()); d.setFechaPago(c.getFechaPago());
        d.setImporte(c.getImporte()); d.setEstado(c.getEstado()); d.setNaturaleza(c.getNaturaleza());
        return d;
    }
}
