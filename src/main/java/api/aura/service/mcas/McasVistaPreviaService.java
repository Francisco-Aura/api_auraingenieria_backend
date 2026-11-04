package api.aura.service.mcas;

import api.aura.dto.mcas.McasCampo;
import api.aura.dto.mcas.McasVistaPreviaDTO;
import api.aura.dto.mcas.McasVistaPreviaDTO.Estado;
import api.aura.entity.Contrato;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.service.mcas.engine.ContextoCalculo;
import api.aura.service.mcas.engine.McasCalculoException;
import api.aura.service.mcas.engine.MotorMcas;
import api.aura.service.mcas.engine.ResultadoCalculo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Comisión estimada del contrato mientras se rellena el formulario, sin
 * guardarlo: el mismo cálculo que al guardar (datos mínimos, interruptores,
 * comisión ya aprobada, selección del conjunto y motor) y los datos que piden las
 * reglas de su comercializadora.
 *
 * <p>Solo lee. El contrato del cuerpo es una copia en memoria sin id (aunque
 * llegue el de un contrato existente, que solo se usa para saber si ya tiene
 * comisión): nunca se guarda ni se crean propuestas, auditoría ni servicios.</p>
 */
@Service
public class McasVistaPreviaService {

    private final McasProposalService propuestas;
    private final MotorMcas motor;
    private final McasConjuntoReglasRepository conjuntoRepo;
    private final TBComercializadorasRepository comercializadoras;
    private final ObjectMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public McasVistaPreviaService(McasProposalService propuestas, MotorMcas motor,
                                  McasConjuntoReglasRepository conjuntoRepo,
                                  TBComercializadorasRepository comercializadoras, ObjectMapper mapper) {
        this.propuestas = propuestas;
        this.motor = motor;
        this.conjuntoRepo = conjuntoRepo;
        this.comercializadoras = comercializadoras;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public McasVistaPreviaDTO calcular(JsonNode cuerpo) {
        Contrato c = leer(cuerpo);
        Integer idExistente = c.getId();
        c.setId(null);
        ContextoCalculo ctx = propuestas.construirContexto(c, "vista-previa-" + UUID.randomUUID());

        // Tabla de comisiones que usaría el cálculo y sus reglas: de ahí salen los campos.
        boolean conComercializadora = ctx.getIdComercializadora() != null && ctx.getIdComercializadora() != 0;
        Optional<McasConjuntoReglas> conjunto = Optional.empty();
        String conjuntosAmbiguos = null;
        if (conComercializadora) {
            try {
                conjunto = motor.seleccionarConjunto(ctx);
            } catch (McasCalculoException e) {
                conjuntosAmbiguos = e.getMessage();
            }
        }
        // Quien graba el contrato no ve códigos internos: sin nombre legible, el de la comercializadora.
        String comercializadora = conComercializadora
                ? comercializadoras.findById(ctx.getIdComercializadora()).map(TBComercializadoras::getNombre).orElse(null)
                : null;
        Campos campos = new Campos(conjunto.map(motor::reglasActivas).orElse(List.of()),
                conComercializadora ? conjuntosConGestora(ctx, comercializadora) : List.of(), comercializadora);

        if (!tipoPrecioValido(c.getMcasTipoPrecio()))
            return campos.sinImporte(Estado.SIN_TIPO_PRECIO,
                    "Indique el tipo de precio (fijo o indexado) para estimar la comisión.", Set.of(McasCampo.TIPO_PRECIO));
        Optional<String> indicadores = propuestas.motivoIndicadores(c.getMcasTipoPrecio());
        // Con el módulo apagado no se crea propuesta: la comisión se gestiona a mano, sin revisión pendiente.
        if (!propuestas.moduloHabilitado())
            return campos.sinImporte(Estado.DESACTIVADO,
                    "El cálculo automático de comisiones está desactivado: la comisión se gestiona manualmente.", Set.of());
        if (indicadores.isPresent())
            return campos.sinImporte(Estado.DESACTIVADO, McasMensajes.paraOperador(indicadores.get()), Set.of());
        if (idExistente != null) {
            Optional<McasPropuesta> aprobada = comisionAprobada(idExistente, c);
            if (aprobada.isPresent()) return yaTieneComision(aprobada.get(), campos);
            // Un «No procede» sigue valiendo aunque cambie el contrato (sin enseñar el motivo del validador).
            if (propuestas.tieneDescarteVigente(idExistente, McasProposalService.tipoEvento(c)))
                return campos.sinImporte(Estado.REQUIERE_REVISION_MANUAL,
                        "Cerrada sin comisión (no procede): el validador revisará si ahora procede.", Set.of());
        }
        // El número de contrato no cambia el importe: no se exige para estimar.
        List<String> basicos = propuestas.camposFaltantes(c).stream().filter(f -> !"numero_contrato".equals(f)).toList();
        if (!basicos.isEmpty())
            return campos.sinImporte(Estado.REQUIERE_DATOS,
                    McasMensajes.paraOperador("Faltan o son invalidos: " + String.join(", ", basicos)), aCampos(basicos));
        if (conjuntosAmbiguos != null)
            return campos.sinImporte(Estado.ERROR_CALCULO, McasMensajes.paraOperador(conjuntosAmbiguos), Set.of());
        if (conjunto.isEmpty())
            return campos.sinImporte(Estado.SIN_REGLAS, McasMensajes.paraOperador(MotorMcas.causaSinConjunto(ctx)),
                    campos.conGestora.isEmpty() || ctx.getIdGestora() != null ? Set.of() : Set.of(McasCampo.GESTORA));

        return conResultado(motor.evaluarConjunto(ctx, conjunto.get()), c, ctx, campos);
    }

    /**
     * Lo que ve quien graba el contrato sin ser administrador: si faltan datos y
     * cuáles, pero no el importe, los pagos, la regla ni los avisos del cálculo
     * (reunión del 04/11/2026: la comisión solo la ven los administradores).
     */
    public static McasVistaPreviaDTO sinImportes(McasVistaPreviaDTO dto) {
        if (dto == null) return null;
        String mensaje = dto.getEstado() == null ? dto.getMensaje() : switch (dto.getEstado()) {
            case CALCULADO -> "Datos de la comisión completos: se calculará al guardar el contrato y la revisará un administrador.";
            case YA_TIENE_COMISION -> "Este contrato ya tiene una comisión aprobada. Los cambios del contrato no generan otra comisión.";
            case REQUIERE_REVISION_MANUAL, ERROR_CALCULO -> "Un administrador revisará la comisión de este contrato en Comisiones MCAS.";
            case NO_COMISIONABLE -> "Este contrato no genera comisión.";
            default -> dto.getMensaje();
        };
        return McasVistaPreviaDTO.builder().estado(dto.getEstado()).mensaje(mensaje).campos(dto.getCampos()).build();
    }

    private McasVistaPreviaDTO conResultado(ResultadoCalculo res, Contrato c, ContextoCalculo ctx, Campos campos) {
        Estado estado = Estado.valueOf(res.getResultado().name());
        List<String> advertencias = res.getAdvertencias() != null ? res.getAdvertencias() : List.of();
        String regla = campos.reglas.stream().filter(r -> Objects.equals(r.getId(), res.getIdRegla())).findFirst()
                .map(r -> McasCamposRequeridos.nombreRegla(r, campos.comercializadora)).orElse(null);
        McasVistaPreviaDTO.McasVistaPreviaDTOBuilder dto = McasVistaPreviaDTO.builder().estado(estado).regla(regla)
                .campos(campos.derivar(aCampos(res.getCamposFaltantes())));
        if (estado != Estado.CALCULADO || res.getImporteTotal() == null)
            return dto.mensaje(advertencias.isEmpty() ? "La comisión se revisará manualmente." : advertencias.get(0))
                    .avisos(advertencias.size() > 1 ? List.copyOf(advertencias.subList(1, advertencias.size())) : List.of())
                    .build();
        int pagos = res.getNumeroPagos() != null ? res.getNumeroPagos() : 1;
        return dto.mensaje("Comisión estimada: " + McasMensajes.importeEnPagos(res.getImporteTotal(), pagos) + ".")
                .importe(res.getImporteTotal()).numeroPagos(pagos)
                .calendario(calendario(res.getImporteTotal(), pagos, c, ctx))
                .avisos(List.copyOf(advertencias))
                .build();
    }

    private McasVistaPreviaDTO yaTieneComision(McasPropuesta aprobada, Campos campos) {
        BigDecimal importe = aprobada.getImporteDefinitivo() != null ? aprobada.getImporteDefinitivo()
                : aprobada.getImporteCalculado();
        return McasVistaPreviaDTO.builder().estado(Estado.YA_TIENE_COMISION)
                .mensaje("Este contrato ya tiene una comisión aprobada: "
                        + McasMensajes.importeEnPagos(importe, aprobada.getNumeroPagos())
                        + ". Los cambios del contrato no generan otra comisión.")
                .importe(importe).numeroPagos(aprobada.getNumeroPagos())
                .campos(campos.derivar(Set.of())).build();
    }

    /** La propuesta con comisión vigente del contrato para este evento (nuevo o renovación), si la hay. */
    private Optional<McasPropuesta> comisionAprobada(Integer idContrato, Contrato c) {
        List<McasPropuesta> delContrato = propuestas.listarPorContrato(idContrato);
        String tipoEvento = McasProposalService.tipoEvento(c);
        if (!propuestas.contratosConComisionVigente(delContrato)
                .contains(McasProposalService.claveComision(idContrato, tipoEvento))) return Optional.empty();
        return delContrato.stream()
                .filter(p -> McasProposalService.ESTADOS_CON_COMISION.contains(p.getEstado()))
                .filter(p -> tipoEvento.equals(p.getTipoEvento()))
                .findFirst();
    }

    /** Mismo calendario que la propuesta: desde la fecha de inicio del contrato, un pago al mes. */
    private List<McasVistaPreviaDTO.Cuota> calendario(BigDecimal importe, int pagos, Contrato c, ContextoCalculo ctx) {
        LocalDate primera = ctx.getFechaEfectiva();
        List<BigDecimal> cuotas = motor.buildPagos(importe, pagos);
        List<McasVistaPreviaDTO.Cuota> lista = new ArrayList<>();
        for (int i = 0; i < cuotas.size(); i++)
            lista.add(McasVistaPreviaDTO.Cuota.builder().numero(i + 1)
                    .fecha(primera != null ? primera.plusMonths(i) : null).importe(cuotas.get(i)).build());
        return lista;
    }

    /** Conjuntos publicados y vigentes de la comercializadora (fase del contrato) que son de una gestora. */
    private List<String> conjuntosConGestora(ContextoCalculo ctx, String comercializadora) {
        String fase = "INDEXADO".equalsIgnoreCase(ctx.getTipoPrecio()) ? "INDEXADO" : "FIJO";
        LocalDate fecha = ctx.getFechaEfectiva() != null ? ctx.getFechaEfectiva() : LocalDate.now();
        return conjuntoRepo.findByIdComercializadoraAndEstado(ctx.getIdComercializadora(), "PUBLICADO").stream()
                .filter(cj -> cj.getIdGestora() != null)
                .filter(cj -> fase.equals(cj.getFasePrecio()) || "AMBOS".equals(cj.getFasePrecio()))
                .filter(cj -> cj.getVigenciaDesde() == null || !fecha.isBefore(cj.getVigenciaDesde()))
                .filter(cj -> cj.getVigenciaHasta() == null || !fecha.isAfter(cj.getVigenciaHasta()))
                .map(cj -> cj.getNombre() != null && !cj.getNombre().isBlank() ? cj.getNombre().trim()
                        : "Tabla de " + (comercializadora != null ? comercializadora : "comisiones") + " para una gestora")
                .distinct().toList();
    }

    /**
     * Dimensiones y categorías de las tablas de las reglas, en una sola consulta:
     * por regla, dimensión → categorías (vacío si la dimensión es numérica).
     */
    private Map<Integer, Map<String, Set<String>>> tablas(List<McasReglaComision> reglas) {
        List<Integer> ids = reglas.stream().map(McasReglaComision::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return Map.of();
        Map<Integer, Map<String, Set<String>>> tablas = new HashMap<>();
        em.createQuery("SELECT DISTINCT v.idRegla, v.dimension, v.valorCategoria FROM McasValorRango v "
                        + "WHERE v.idRegla IN :ids", Object[].class)
                .setParameter("ids", ids).getResultList()
                .forEach(fila -> {
                    Set<String> categorias = tablas.computeIfAbsent((Integer) fila[0], k -> new TreeMap<>())
                            .computeIfAbsent((String) fila[1], k -> new TreeSet<>());
                    if (fila[2] != null) categorias.add((String) fila[2]);
                });
        return tablas;
    }

    private Set<McasCampo> aCampos(Collection<String> codigos) {
        Set<McasCampo> campos = EnumSet.noneOf(McasCampo.class);
        if (codigos != null) codigos.forEach(cod -> McasCampo.desdeCodigo(cod).ifPresent(campos::add));
        return campos;
    }

    private boolean tipoPrecioValido(String tipo) {
        return tipo != null && List.of("FIJO", "INDEXADO").contains(tipo.trim().toUpperCase(Locale.ROOT));
    }

    private Contrato leer(JsonNode cuerpo) {
        if (cuerpo == null || !cuerpo.isObject())
            throw new IllegalArgumentException("Envíe los datos del contrato");
        try {
            return mapper.treeToValue(cuerpo, Contrato.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Los datos del contrato no son válidos: revise los números y las fechas");
        }
    }

    /** Reglas de la tabla y gestoras, para derivar los campos con los que falten en cada caso. */
    private final class Campos {
        private final List<McasReglaComision> reglas;
        private final List<String> conGestora;
        private final String comercializadora;
        private final Map<Integer, Map<String, Set<String>>> tablas;

        Campos(List<McasReglaComision> reglas, List<String> conGestora, String comercializadora) {
            this.reglas = reglas;
            this.conGestora = conGestora;
            this.comercializadora = comercializadora;
            this.tablas = tablas(reglas);
        }

        List<McasVistaPreviaDTO.Campo> derivar(Set<McasCampo> faltan) {
            return McasCamposRequeridos.derivar(reglas, tablas, conGestora, faltan, comercializadora);
        }

        McasVistaPreviaDTO sinImporte(Estado estado, String mensaje, Set<McasCampo> faltan) {
            return McasVistaPreviaDTO.builder().estado(estado).mensaje(mensaje).campos(derivar(faltan)).build();
        }
    }
}
