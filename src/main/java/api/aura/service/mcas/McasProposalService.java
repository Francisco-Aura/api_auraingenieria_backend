package api.aura.service.mcas;

import api.aura.config.AuraUserHolder;
import api.aura.entity.Contrato;
import api.aura.entity.Servicio;
import api.aura.entity.ServiciosPagos;
import api.aura.entity.mcas.*;
import api.aura.repository.ContratoRepository;
import api.aura.repository.ServicioRepository;
import api.aura.repository.ServiciosPagosRepository;
import api.aura.repository.mcas.*;
import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.entity.tablas_maestras.TBProductos;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;
import api.aura.repository.tablas_maestras.TBProductosRepository;
import api.aura.repository.tablas_maestras.TBTarifasGasRepository;
import api.aura.repository.tablas_maestras.TBTarifasLuzRepository;
import api.aura.service.ModuloConfiguracionService;
import api.aura.service.mcas.engine.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Servicio principal de orquestación MCAS.
 *
 * Flujo:
 * guardarContrato() → triggerDesdeContrato() → PENDIENTE_VALIDACION
 * PENDIENTE_VALIDACION → aprobar()   → crea Servicio → FINALIZADO_APROBADO
 * PENDIENTE_VALIDACION → rechazar()  → RECHAZADO → EN_EDICION (mismo paso)
 * REQUIERE_REVISION_MANUAL / REQUIERE_DATOS / ERROR_CALCULO → abrirEdicion() → EN_EDICION
 * EN_EDICION → finalizarEditado()    → crea Servicio → FINALIZADO_EDITADO
 *
 * Invariantes:
 * - importe_calculado NUNCA se sobrescribe
 * - clave_idempotencia se libera al superseder (evita UNIQUE violation)
 * - @Version previene doble aprobación concurrente
 */
@Service
@RequiredArgsConstructor
public class McasProposalService {

    private static final Logger log = LoggerFactory.getLogger(McasProposalService.class);

    private final ContratoRepository contratoRepo;
    private final McasPropuestaRepository propuestaRepo;
    private final McasLineaPropuestaRepository lineaRepo;
    private final McasCalendarioPagoRepository calendarioRepo;
    private final McasAuditoriaRepository auditoriaRepo;
    private final McasProductoComercialRepository productoRepo;
    private final MotorMcas motor;
    private final McasAuditoriaService auditoria;
    private final ModuloConfiguracionService flags;
    private final ObjectMapper mapper;
    private final McasContratoSnapshotService snapshots;
    private final McasRetrocesionPolicyService retrocesionPolicies;
    private final ServicioRepository servicioRepo;
    private final ServiciosPagosRepository serviciosPagosRepo;
    private final TBPagoPlazosRepository pagoPlazosRepo;
    private final TBTarifasLuzRepository tarifasLuzRepo;
    private final TBTarifasGasRepository tarifasGasRepo;
    private final TBProductosRepository productosServicioRepo;
    private final McasCondicionesCobroService condicionesCobro;
    private final McasZonasService zonas;

    // Feature flag codes
    public static final String FLAG_ENABLED      = "MCAS_ENABLED";
    public static final String FLAG_FIJO         = "MCAS_FIXED_ENABLED";
    public static final String FLAG_INDEXADO     = "MCAS_INDEXED_ENABLED";
    public static final String FLAG_RETROCESION  = "MCAS_RETROCESSION_ENABLED";
    /** Con este interruptor, la propuesta calculada al guardar el contrato se aprueba sola. */
    public static final String FLAG_APROBACION_AUTOMATICA = "MCAS_AUTO_APPROVAL_ENABLED";
    /** Quién figura como aprobador de las propuestas aprobadas solas. */
    public static final String USUARIO_APROBACION_AUTOMATICA = "Aprobación automática";

    /**
     * Indica si el módulo MCAS está habilitado globalmente. Se usa para decidir,
     * antes de persistir nada ni bloquear el contrato, si el disparo automático
     * debe ejecutarse: con el flag desactivado MCAS es completamente inerte en el
     * flujo de guardado de contratos (no crea propuestas ni toma bloqueos).
     */
    public boolean moduloHabilitado() {
        return flags.estaActivo(FLAG_ENABLED);
    }

    private static final List<String> ESTADOS_TERMINALES =
            List.of("FINALIZADO_APROBADO", "FINALIZADO_EDITADO", "FINALIZADO_NO_COMISIONABLE",
                    "CANCELADO", "SUPERADO", "DESACTUALIZADO");

    /** Estados en los que la propuesta ya generó su servicio de comisión. */
    public static final List<String> ESTADOS_CON_COMISION =
            List.of("FINALIZADO_APROBADO", "FINALIZADO_EDITADO");

    /**
     * Por qué los interruptores impiden calcular este tipo de precio (causa
     * técnica: «MCAS_ENABLED=false»...), o vacío si se puede calcular. Lo usan el
     * cálculo real y la vista previa del contrato.
     */
    public Optional<String> motivoIndicadores(String tipoPrecio) {
        String tipo = tipoPrecio != null ? tipoPrecio.trim() : null;
        if (!flags.estaActivo(FLAG_ENABLED)) return Optional.of("MCAS_ENABLED=false");
        if ("INDEXADO".equalsIgnoreCase(tipo) && !flags.estaActivo(FLAG_INDEXADO))
            return Optional.of("Contratos INDEXADO no habilitados (MCAS_INDEXED_ENABLED=false)");
        if ("FIJO".equalsIgnoreCase(tipo) && !flags.estaActivo(FLAG_FIJO))
            return Optional.of("Fase FIJO no habilitada (MCAS_FIXED_ENABLED=false)");
        return Optional.empty();
    }

    /**
     * Producto del catálogo de servicios (TBProductos) que usan en Aura las
     * comisiones por cambio de comercializadora. Se busca por nombre porque el
     * id depende de cada instalación.
     */
    private static final String PRODUCTO_SERVICIO_COMISION = "SWITCHING";

    // ============================================================
    // TRIGGER desde ContratoService
    // ============================================================

    /**
     * Supera propuestas activas no finalizadas y lanza cálculo fresco.
     * Libera clave_idempotencia al superseder para evitar UNIQUE violation.
     */
    @Transactional
    public McasPropuesta triggerDesdeContrato(Integer idContrato) {
        Contrato contrato = contratoRepo.findByIdForUpdate(idContrato)
                .orElseThrow(() -> new IllegalArgumentException("Contrato no encontrado: " + idContrato));
        // La zona sale de la provincia del punto: se pone antes de la instantánea para que cuente
        zonas.aplicar(contrato);
        McasContratoSnapshotService.Snapshot actual = snapshots.capturar(contrato);
        propuestaRepo.findActivasPorContrato(idContrato).stream()
                .filter(p -> !ESTADOS_TERMINALES.contains(p.getEstado()))
                .forEach(p -> {
                    if (actual.hash().equals(p.getHashContrato())) return;
                    String prev = p.getEstado();
                    liberarClave(p, "stale");
                    p.setEstado("DESACTUALIZADO");
                    p.setActualizadoEn(LocalDateTime.now());
                    propuestaRepo.save(p);
                    auditoria.registrar(p.getId(), idContrato, "PROPUESTA_DESACTUALIZADA",
                            prev, "DESACTUALIZADO", Map.of("hash_actual", actual.hash()), "sistema", null);
                });
        Optional<McasPropuesta> misma = propuestaRepo.findActivasPorContrato(idContrato).stream()
                .filter(p -> !ESTADOS_TERMINALES.contains(p.getEstado()))
                .filter(p -> actual.hash().equals(p.getHashContrato()))
                .findFirst();
        if (misma.isPresent()) return misma.get();
        return iniciarCalculo(idContrato);
    }

    // ============================================================
    // CALCULAR (idempotente)
    // ============================================================

    @Transactional
    public McasPropuesta iniciarCalculo(Integer idContrato) {
        String usuario = usuario();
        String corr = UUID.randomUUID().toString();
        Contrato c = contratoRepo.findByIdForUpdate(idContrato)
                .orElseThrow(() -> new IllegalArgumentException("Contrato no encontrado: " + idContrato));
        zonas.aplicar(c);
        McasContratoSnapshotService.Snapshot snapshot = snapshots.capturar(c);

        // Validar campos mínimos
        List<String> faltantes = camposFaltantes(c);
        if (!faltantes.isEmpty())
            return crearPropuesta(c, "REQUIERE_DATOS",
                    "Faltan o son invalidos: " + String.join(", ", faltantes), faltantes, corr, usuario, snapshot);

        // Feature flags
        Optional<String> indicadores = motivoIndicadores(c.getMcasTipoPrecio());
        if (indicadores.isPresent())
            return crearPropuesta(c, "REQUIERE_REVISION_MANUAL", indicadores.get(), List.of(), corr, usuario, snapshot);

        // Idempotencia
        String clave = clave(c, snapshot.hash());
        Optional<McasPropuesta> existente = propuestaActivaPorClave(clave, usuario, corr);
        if (existente.isPresent()) {
            log.info("MCAS idempotencia: propuesta {} ya activa contrato={}", existente.get().getId(), idContrato);
            return existente.get();
        }

        // Una sola comisión por contrato y tipo de evento: si ya se aprobó una, los
        // cambios posteriores del contrato no generan otra propuesta aprobable.
        Optional<McasPropuesta> conComision = comisionYaGenerada(idContrato, tipoEvento(c), null);
        if (conComision.isPresent()) {
            McasPropuesta aprobada = conComision.get();
            Set<String> cambios = cambiosRespectoA(aprobada, snapshot);
            // Nada que influya en la comisión (p. ej. solo la fecha fin, o una clave que ya no
            // forma parte de la instantánea): la aprobada sigue valiendo tal cual.
            if (cambios.isEmpty()) return aprobada;
            // Solo la fecha de inicio (se grabó estimada y se ajusta unos días; respuesta del
            // cliente a la duda 15): se mueven las cuotas pendientes del servicio, sin avisos.
            if (cambios.equals(Set.of(CLAVE_FECHA_INICIO))) return moverCobros(aprobada, c, snapshot, usuario, corr);
            return crearPropuesta(c, "REQUIERE_REVISION_MANUAL",
                    mensajeComisionExistente(aprobada), List.of(), corr, usuario, snapshot);
        }

        // Un «No procede» sigue valiendo aunque el contrato cambie: la propuesta nueva
        // va a revisión manual (no se puede aprobar en lote) hasta que el validador
        // decida o reabra la descartada con «Recalcular».
        Optional<McasPropuesta> descartada = descarteVigente(idContrato, tipoEvento(c));
        if (descartada.isPresent())
            return crearPropuesta(c, "REQUIERE_REVISION_MANUAL", mensajeDescarte(descartada.get()), List.of(),
                    Map.of(TRAZA_DESCARTE_PREVIO, descartada.get().getId()), corr, usuario, snapshot);

        // Calcular
        ContextoCalculo ctx = construirContexto(c, corr);
        ResultadoCalculo res = motor.evaluar(ctx);
        McasPropuesta p = buildPropuesta(c, ctx, res, clave, usuario, snapshot);
        p = propuestaRepo.save(p);
        if (res.getLineas() != null && !res.getLineas().isEmpty()) guardarLineas(p, res);
        if (res.getResultado() == ResultadoCalculo.Resultado.CALCULADO && res.getImporteTotal() != null)
            guardarCalendario(c, p, res);
        auditoria.registrar(p.getId(), idContrato, "PROPUESTA_CALCULADA", null, p.getEstado(), res, usuario, corr);
        log.info("MCAS contrato={} propuesta={} estado={}", idContrato, p.getId(), p.getEstado());
        return p;
    }

    /**
     * Sustituye la propuesta por un cálculo nuevo. Además de las abiertas y las
     * desactualizadas, reabre una cerrada sin comisión que no creó servicio (un
     * «No procede» por error o un caso que la regla daba por no comisionable).
     */
    @Transactional
    public McasPropuesta recalcular(Long propuestaId) {
        McasPropuesta p = cargar(propuestaId);
        boolean cerradaSinServicio = "FINALIZADO_NO_COMISIONABLE".equals(p.getEstado()) && p.getIdServicioCreado() == null;
        if (ESTADOS_TERMINALES.contains(p.getEstado()) && !"DESACTUALIZADO".equals(p.getEstado()) && !cerradaSinServicio)
            throw new IllegalStateException("Esta propuesta no se puede recalcular (estado actual: «"
                    + McasMensajes.estadoPropuesta(p.getEstado()) + "»).");
        Integer idContrato = p.getIdContrato();
        String prev = p.getEstado();
        String usuario = usuario();
        superar(p);
        if (cerradaSinServicio) {
            auditoria.registrar(p.getId(), idContrato, "PROPUESTA_REABIERTA", prev, "SUPERADO", null, usuario, null);
            // Las propuestas que quedaron en revisión solo por este «No procede» ya no tienen motivo.
            Long idDescartada = p.getId();
            propuestaRepo.findByIdContratoOrderByCreadoEnDesc(idContrato).stream()
                    .filter(x -> "REQUIERE_REVISION_MANUAL".equals(x.getEstado()))
                    .filter(x -> Objects.equals(descartePrevio(x), idDescartada))
                    .forEach(x -> {
                        superar(x);
                        auditoria.registrar(x.getId(), idContrato, "PROPUESTA_SUPERADA",
                                "REQUIERE_REVISION_MANUAL", "SUPERADO", null, usuario, null);
                    });
        } else {
            // Como el resto de cambios de estado: queda quién la sustituyó por un cálculo nuevo
            auditoria.registrar(p.getId(), idContrato, "PROPUESTA_SUPERADA", prev, "SUPERADO", null, usuario, null);
        }
        return triggerDesdeContrato(idContrato);
    }

    // ============================================================
    // NO PROCEDE → cerrada sin comisión
    // ============================================================

    /** Estados en los que el validador puede cerrar la propuesta sin comisión. */
    public static final List<String> ESTADOS_DESCARTABLES = List.of("PENDIENTE_CALCULO", "PENDIENTE_VALIDACION",
            "REQUIERE_DATOS", "REQUIERE_REVISION_MANUAL", "ERROR_CALCULO", "RECHAZADO", "EN_EDICION");

    /**
     * «No procede»: cierra la propuesta sin comisión ni servicio con el motivo
     * del validador (en {@code motivoRechazo} y en la auditoría). La clave de
     * idempotencia no se libera: volver a guardar el contrato sin cambios devuelve
     * esta misma propuesta en vez de calcular otra. {@link #recalcular} la reabre.
     */
    @Transactional
    public McasPropuesta descartar(Long propuestaId, String motivo) {
        if (motivo == null || motivo.isBlank())
            throw new IllegalArgumentException("Indique el motivo por el que no procede la comisión");
        String usuario = usuario();
        McasPropuesta p = cargar(propuestaId);
        if (!ESTADOS_DESCARTABLES.contains(p.getEstado()) || p.getIdServicioCreado() != null)
            throw new IllegalStateException("Esta propuesta ya está cerrada: no se puede marcar como «No procede»");
        String prev = p.getEstado();
        String texto = motivo.trim();
        LocalDateTime ahora = LocalDateTime.now();
        p.setEstado("FINALIZADO_NO_COMISIONABLE");
        p.setMotivoRechazo(texto);
        // Marca del «No procede» (una regla NOT_COMMISSIONABLE no la lleva): la usan
        // el cálculo de propuestas posteriores y el resumen del contrato.
        Map<String, Object> descarte = new LinkedHashMap<>();
        descarte.put("motivo", texto);
        descarte.put("fecha", ahora.toLocalDate().toString());
        descarte.put("usuario", usuario);
        p.setDatosEditadosJson(conClave(p.getDatosEditadosJson(), CLAVE_DESCARTE, descarte));
        p.setActualizadoPor(usuario); p.setActualizadoEn(ahora);
        p = propuestaRepo.saveAndFlush(p); // la comprobación de versión salta aquí, antes de la auditoría
        auditoria.registrar(p.getId(), p.getIdContrato(), "PROPUESTA_DESCARTADA", prev, "FINALIZADO_NO_COMISIONABLE",
                Map.of("motivo", texto), usuario, null);
        return p;
    }

    /** Clave de datos_editados_json con la marca del «No procede». */
    private static final String CLAVE_DESCARTE = "descarte";
    /** Clave de la traza: propuesta descartada que mandó esta a revisión manual. */
    private static final String TRAZA_DESCARTE_PREVIO = "descarte_previo";

    /** Si la propuesta está cerrada por un «No procede» del validador (no por una regla no comisionable). */
    public boolean esDescarte(McasPropuesta p) {
        return "FINALIZADO_NO_COMISIONABLE".equals(p.getEstado()) && marcaDescarte(p).isPresent();
    }

    private Optional<com.fasterxml.jackson.databind.JsonNode> marcaDescarte(McasPropuesta p) {
        if (p.getDatosEditadosJson() == null || p.getDatosEditadosJson().isBlank()) return Optional.empty();
        try {
            var marca = mapper.readTree(p.getDatosEditadosJson()).path(CLAVE_DESCARTE);
            return marca.isObject() ? Optional.of(marca) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Si el contrato tiene un «No procede» sin reabrir para ese evento (vista previa). */
    public boolean tieneDescarteVigente(Integer idContrato, String tipoEvento) {
        return descarteVigente(idContrato, tipoEvento).isPresent();
    }

    /** Si la propuesta está en revisión solo porque el contrato cambió tras un «No procede». */
    public boolean vieneDeDescarte(McasPropuesta p) {
        return descartePrevio(p) != null;
    }

    /** «No procede» vigente del contrato para ese evento: el más reciente que no se ha reabierto. */
    private Optional<McasPropuesta> descarteVigente(Integer idContrato, String tipoEvento) {
        return propuestaRepo.findByIdContratoOrderByCreadoEnDesc(idContrato).stream()
                .filter(x -> Objects.equals(x.getTipoEvento(), tipoEvento))
                .filter(this::esDescarte)
                .findFirst();
    }

    /** «Se marcó como No procede el 30/09/2026: <motivo>. Revise si ahora procede.» */
    private String mensajeDescarte(McasPropuesta descartada) {
        var marca = marcaDescarte(descartada).orElseThrow();
        String motivo = marca.path("motivo").asText(descartada.getMotivoRechazo() != null ? descartada.getMotivoRechazo() : "");
        motivo = motivo.trim().replaceAll("[.\\s]+$", "");
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(marca.path("fecha").asText());
        } catch (Exception e) {
            fecha = descartada.getActualizadoEn() != null ? descartada.getActualizadoEn().toLocalDate() : LocalDate.now();
        }
        return "Se marcó como No procede el " + fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + (motivo.isEmpty() ? "" : ": " + motivo) + ". Revise si ahora procede.";
    }

    /** Id de la propuesta descartada que mandó esta a revisión manual, o null. */
    private Long descartePrevio(McasPropuesta p) {
        if (p.getTrazaCalculoJson() == null) return null;
        try {
            var id = mapper.readTree(p.getTrazaCalculoJson()).path(TRAZA_DESCARTE_PREVIO);
            return id.canConvertToLong() ? id.asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** El JSON dado (objeto o vacío) con una clave más. */
    private String conClave(String json, String clave, Object valor) {
        Map<String, Object> datos = new LinkedHashMap<>();
        if (json != null && !json.isBlank()) {
            try {
                datos.putAll(mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                datos.put("anterior", json);
            }
        }
        datos.put(clave, valor);
        return toJson(datos);
    }

    // ============================================================
    // APROBAR → crea Servicio
    // ============================================================

    @Transactional(noRollbackFor = McasPropuestaDesactualizadaException.class)
    public McasPropuesta aprobar(Long propuestaId) {
        return aprobarComo(propuestaId, usuario(), false);
    }

    /**
     * Aprobación sin validador, tras guardar el contrato, si está encendido el
     * interruptor «Aprobación automática» (Configuración MCAS). Solo aprueba las
     * propuestas calculadas (pendientes de validación); con el interruptor apagado
     * o en otro estado no hace nada y devuelve vacío. Queda auditada como automática.
     */
    @Transactional(noRollbackFor = McasPropuestaDesactualizadaException.class)
    public Optional<McasPropuesta> aprobarAutomaticamente(Long propuestaId) {
        if (!aprobacionAutomaticaActiva()) return Optional.empty();
        McasPropuesta p = cargar(propuestaId);
        if (!"PENDIENTE_VALIDACION".equals(p.getEstado()) || p.getIdServicioCreado() != null) return Optional.empty();
        return Optional.of(aprobarComo(propuestaId, USUARIO_APROBACION_AUTOMATICA, true));
    }

    public boolean aprobacionAutomaticaActiva() {
        return flags.estaActivo(FLAG_APROBACION_AUTOMATICA);
    }

    private McasPropuesta aprobarComo(Long propuestaId, String usuario, boolean automatica) {
        McasPropuesta p = cargar(propuestaId);
        if (!"PENDIENTE_VALIDACION".equals(p.getEstado()))
            throw new IllegalStateException("Solo se puede aprobar una propuesta pendiente de validación (estado actual: «"
                    + McasMensajes.estadoPropuesta(p.getEstado()) + "»).");
        if (p.getIdServicioCreado() != null)
            throw new IllegalStateException("Esta propuesta ya creó el servicio #" + p.getIdServicioCreado() + ".");

        Integer idContrato = p.getIdContrato();
        Contrato c = contratoRepo.findByIdForUpdate(idContrato)
                .orElseThrow(() -> new IllegalArgumentException("Contrato no encontrado: " + idContrato));
        exigirSinComisionPrevia(p);
        validarVigenciaPropuesta(p, c, usuario);
        Servicio s = crearServicio(p, c, usuario, false);

        String prev = p.getEstado();
        p.setEstado("FINALIZADO_APROBADO");
        p.setIdServicioCreado(s.getId());
        p.setAprobadoPor(usuario); p.setAprobadoEn(LocalDateTime.now());
        p.setActualizadoPor(usuario); p.setActualizadoEn(LocalDateTime.now());
        p = propuestaRepo.saveAndFlush(p); // la comprobación de versión salta aquí, antes de la auditoría
        auditoria.registrar(p.getId(), idContrato, "PROPUESTA_APROBADA", prev, "FINALIZADO_APROBADO",
                automatica ? Map.of("id_servicio", s.getId(), "automatica", true) : Map.of("id_servicio", s.getId()),
                usuario, null);
        log.info("MCAS propuesta={} APROBADA{} servicio={}", propuestaId, automatica ? " (automática)" : "", s.getId());
        return p;
    }

    // ============================================================
    // RECHAZAR
    // ============================================================

    /**
     * Rechazar no cierra la comisión: el cliente pidió que «de rechazar siga a
     * editar y de ahí, una vez editado, a servicio definitivo». La propuesta pasa
     * por RECHAZADO (queda auditado) y en la misma transacción se abre la edición.
     */
    @Transactional
    public McasPropuesta rechazar(Long propuestaId, String motivo) {
        if (motivo == null || motivo.isBlank())
            throw new IllegalArgumentException("Indique el motivo del rechazo.");
        String usuario = usuario();
        McasPropuesta p = cargar(propuestaId);
        if (!"PENDIENTE_VALIDACION".equals(p.getEstado()))
            throw new IllegalStateException("Solo se puede rechazar una propuesta pendiente de validación (estado actual: «"
                    + McasMensajes.estadoPropuesta(p.getEstado()) + "»).");
        String prev = p.getEstado();
        p.setEstado("RECHAZADO"); p.setMotivoRechazo(motivo);
        p.setActualizadoPor(usuario); p.setActualizadoEn(LocalDateTime.now());
        p = propuestaRepo.saveAndFlush(p); // la comprobación de versión salta aquí, antes de la auditoría
        auditoria.registrar(p.getId(), p.getIdContrato(), "PROPUESTA_RECHAZADA",
                prev, "RECHAZADO", Map.of("motivo", motivo), usuario, null);
        return pasarAEdicion(p, usuario);
    }

    // ============================================================
    // EDICIÓN
    // ============================================================

    /**
     * Estados desde los que el operador puede completar la comisión a mano: una
     * propuesta rechazada o una que el motor no pudo calcular. Ninguna comisión se
     * queda sin salida hacia un servicio definitivo.
     */
    public static final List<String> ESTADOS_EDITABLES =
            List.of("RECHAZADO", "REQUIERE_REVISION_MANUAL", "REQUIERE_DATOS", "ERROR_CALCULO");

    @Transactional
    public McasPropuesta abrirEdicion(Long propuestaId) {
        String usuario = usuario();
        McasPropuesta p = cargar(propuestaId);
        if (!ESTADOS_EDITABLES.contains(p.getEstado()))
            throw new IllegalStateException("Solo se puede completar a mano una propuesta rechazada o pendiente de "
                    + "revisión manual (estado actual: «" + McasMensajes.estadoPropuesta(p.getEstado()) + "»).");
        // Una comisión por contrato y evento: si ya hay una aprobada, la propuesta se
        // queda como está y el usuario ve qué comisión existe, en vez de editar algo
        // que al finalizar se rechazaría.
        exigirSinComisionPrevia(p);
        return pasarAEdicion(p, usuario);
    }

    private McasPropuesta pasarAEdicion(McasPropuesta p, String usuario) {
        String prev = p.getEstado();
        p.setEstado("EN_EDICION");
        p.setActualizadoPor(usuario); p.setActualizadoEn(LocalDateTime.now());
        p = propuestaRepo.saveAndFlush(p); // la comprobación de versión salta aquí, antes de la auditoría
        auditoria.registrar(p.getId(), p.getIdContrato(), "EDICION_ABIERTA", prev, "EN_EDICION",
                Map.of("origen", prev), usuario, null);
        return p;
    }

    @Transactional(noRollbackFor = McasPropuestaDesactualizadaException.class)
    public McasPropuesta finalizarEditado(Long propuestaId, BigDecimal importeDefinitivo,
                                           Integer numeroPagos, String overrideJson, String motivoEdicion) {
        String usuario = usuario();
        McasPropuesta p = cargar(propuestaId);
        if (!"EN_EDICION".equals(p.getEstado()))
            throw new IllegalStateException("Solo se puede terminar la corrección de una propuesta en edición (estado actual: «"
                    + McasMensajes.estadoPropuesta(p.getEstado()) + "»).");
        if (p.getIdServicioCreado() != null)
            throw new IllegalStateException("Esta propuesta ya creó el servicio #" + p.getIdServicioCreado() + ".");
        if (importeDefinitivo != null && importeDefinitivo.signum() < 0)
            throw new IllegalArgumentException("El importe definitivo no puede ser negativo.");
        // Al céntimo, como las cuotas: «100,555» creaba un servicio de 100.555 con cuotas
        // que sumaban 100,56
        if (importeDefinitivo != null) importeDefinitivo = importeDefinitivo.setScale(2, java.math.RoundingMode.HALF_UP);
        // Una propuesta que el motor no calculó (revisión manual) no tiene importe al que volver.
        if (importeDefinitivo == null && p.getImporteCalculado() == null)
            throw new IllegalArgumentException("Indique el importe definitivo de la comisión.");
        if (numeroPagos != null && (numeroPagos < 1 || numeroPagos > 12))
            throw new IllegalArgumentException("El número de pagos debe estar entre 1 y 12.");
        if (motivoEdicion == null || motivoEdicion.isBlank())
            throw new IllegalArgumentException("Indique el motivo de la corrección.");

        Integer idContrato = p.getIdContrato();
        Contrato c = contratoRepo.findByIdForUpdate(idContrato)
                .orElseThrow(() -> new IllegalArgumentException("Contrato no encontrado: " + idContrato));
        // Una propuesta completada a mano puede venir de un contrato con datos
        // pendientes: sin fecha ni comercializadora el servicio quedaría sin fechas
        // de pago ni comercializadora.
        if (c.getFechaInicio() == null || c.getIdComercializadoraActual() == null || c.getIdComercializadoraActual() == 0)
            throw new IllegalArgumentException(
                    "Complete la fecha de inicio y la comercializadora del contrato antes de crear el servicio.");
        exigirSinComisionPrevia(p);
        // La vigencia se comprueba antes de tocar la propuesta o su calendario. La
        // excepción conserva únicamente el cambio a DESACTUALIZADO y su auditoría.
        validarVigenciaPropuesta(p, c, usuario);

        // Sin número de pagos se mantiene el de la propuesta; si no lo tiene (no se
        // calculó), el del contrato o un único pago.
        Integer pagos = numeroPagos != null ? numeroPagos
                : p.getNumeroPagos() != null && p.getNumeroPagos() > 0 ? p.getNumeroPagos()
                : Objects.requireNonNullElse(pagosDeLaComercializadora(c), 1);
        p.setDatosEditadosJson(datosEdicionJson(p, importeDefinitivo, pagos, overrideJson, motivoEdicion));
        if (importeDefinitivo != null) p.setImporteDefinitivo(importeDefinitivo);
        p.setNumeroPagos(pagos);

        // Reconstruir calendario con importe editado (y la fecha del primer cobro, que las
        // propuestas que no pasaron por el motor no tienen)
        BigDecimal imp = p.getImporteDefinitivo() != null ? p.getImporteDefinitivo() : p.getImporteCalculado();
        reconstruirCalendario(c, p, imp, pagos);
        if (lineaRepo.findByIdPropuestaOrderByOrdenAsc(p.getId()).isEmpty())
            lineaRepo.save(McasLineaPropuesta.builder()
                    .idPropuesta(p.getId()).tipoLinea("AJUSTE").metodo("MANUAL")
                    .descripcion("Importe introducido manualmente")
                    // importe_calculado es NOT NULL: el motor no calculó nada.
                    .importeCalculado(BigDecimal.ZERO).importeDefinitivo(imp).orden(1).build());
        else
            actualizarLineasDefinitivas(p, imp);

        Servicio s = crearServicio(p, c, usuario, true);

        String prev = p.getEstado();
        p.setEstado("FINALIZADO_EDITADO"); p.setIdServicioCreado(s.getId());
        p.setAprobadoPor(usuario); p.setAprobadoEn(LocalDateTime.now());
        p.setActualizadoPor(usuario); p.setActualizadoEn(LocalDateTime.now());
        p = propuestaRepo.saveAndFlush(p); // la comprobación de versión salta aquí, antes de la auditoría
        auditoria.registrar(p.getId(), idContrato, "PROPUESTA_FINALIZADA_EDITADA",
                prev, "FINALIZADO_EDITADO",
                Map.of("importe_definitivo", imp != null ? imp : 0, "motivo", motivoEdicion != null ? motivoEdicion : ""),
                usuario, null);
        return p;
    }

    // ============================================================
    // CONSULTA
    // ============================================================

    public List<McasPropuesta> listarTodas() { return propuestaRepo.findTodasOrdenadas(); }
    public List<McasPropuesta> listarPorContrato(Integer idContrato) {
        return propuestaRepo.findByIdContratoOrderByCreadoEnDesc(idContrato);
    }
    public Optional<McasPropuesta> obtener(Long id) { return propuestaRepo.findById(id); }
    public List<McasLineaPropuesta> obtenerLineas(Long id) { return lineaRepo.findByIdPropuestaOrderByOrdenAsc(id); }
    public List<McasCalendarioPago> obtenerCalendario(Long id) { return calendarioRepo.findByIdPropuestaOrderByNumeroPagoAsc(id); }
    public List<McasAuditoria> obtenerAuditoria(Long id) { return auditoriaRepo.findByIdPropuestaOrderByFechaEventoAsc(id); }
    public List<McasAuditoria> obtenerAuditoriaPorContrato(Integer idContrato) { return auditoriaRepo.findByIdContratoOrderByFechaEventoAsc(idContrato); }

    // ============================================================
    // HELPERS INTERNOS
    // ============================================================

    private McasPropuesta cargar(Long id) {
        return propuestaRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la propuesta de comisión #" + id + "."));
    }

    private String usuario() {
        String u = AuraUserHolder.get();
        return u != null ? u : "sistema";
    }

    /** Deja la propuesta como sustituida por un cálculo nuevo (con quién y cuándo). */
    private void superar(McasPropuesta p) {
        liberarClave(p, "sup");
        p.setEstado("SUPERADO");
        p.setActualizadoPor(usuario());
        p.setActualizadoEn(LocalDateTime.now());
        propuestaRepo.save(p);
    }

    /**
     * Libera la clave de idempotencia de una propuesta que deja de estar activa
     * para que un cálculo nuevo pueda reutilizarla. La clave liberada no crece
     * con cada liberación (el índice único admite como máximo 190 caracteres).
     */
    private void liberarClave(McasPropuesta p, String motivo) {
        p.setClaveIdempotencia("LIBERADA|" + p.getId() + "|" + motivo + "|" + UUID.randomUUID());
    }

    /**
     * La propuesta que ocupa la clave del contrato en su estado actual, si sigue activa.
     * Una comisión aprobada cuyo servicio se borró ya no cuenta como comisión: su clave se
     * libera para que el contrato, aunque no haya cambiado, se pueda volver a calcular. Antes
     * «Calcular propuesta» devolvía la aprobada antigua sin servicio.
     */
    private Optional<McasPropuesta> propuestaActivaPorClave(String clave, String usuario, String corr) {
        Optional<McasPropuesta> existente = propuestaRepo.findByClaveIdempotencia(clave)
                .filter(p -> !List.of("CANCELADO", "SUPERADO").contains(p.getEstado()));
        if (existente.isPresent() && ESTADOS_CON_COMISION.contains(existente.get().getEstado())
                && !tieneServicio(existente.get())) {
            McasPropuesta sinServicio = existente.get();
            liberarClave(sinServicio, "sin-servicio");
            // Antes del alta de la nueva: Hibernate inserta antes de actualizar y la clave es única
            propuestaRepo.saveAndFlush(sinServicio);
            auditoria.registrar(sinServicio.getId(), sinServicio.getIdContrato(), "COMISION_SIN_SERVICIO",
                    sinServicio.getEstado(), sinServicio.getEstado(),
                    Map.of("idServicioCreado", String.valueOf(sinServicio.getIdServicioCreado())), usuario, corr);
            return Optional.empty();
        }
        return existente;
    }

    /**
     * Pagos en que cobra la comercializadora del contrato (o su gestora), de sus condiciones
     * de cobro; null si no los tiene configurados. Desde el 05/11/2026 el contrato ya no
     * tiene «Nº de pagos» (respuesta del cliente a la duda 8): un valor antiguo se ignora.
     */
    private Integer pagosDeLaComercializadora(Contrato c) {
        return condicionesCobro.numeroPagos(c.getIdComercializadoraActual(), c.getMcasIdGestora());
    }

    /** Tipo de evento de la propuesta del contrato: RENOVACION o NUEVO_CONTRATO. */
    public static String tipoEvento(Contrato c) {
        return Boolean.TRUE.equals(c.getMcasEsRenovacion()) ? "RENOVACION" : "NUEVO_CONTRATO";
    }

    /** Propuesta del mismo contrato y tipo de evento que ya generó servicio de comisión. */
    private Optional<McasPropuesta> comisionYaGenerada(Integer idContrato, String tipoEvento, Long excluida) {
        return propuestaRepo.findByIdContratoOrderByCreadoEnDesc(idContrato).stream()
                .filter(x -> !Objects.equals(x.getId(), excluida))
                .filter(x -> ESTADOS_CON_COMISION.contains(x.getEstado()))
                .filter(x -> Objects.equals(x.getTipoEvento(), tipoEvento))
                .filter(this::tieneServicio)
                .findFirst();
    }

    /** Clave «contrato|tipo de evento» de {@link #contratosConComisionVigente}. */
    public static String claveComision(Integer idContrato, String tipoEvento) {
        return idContrato + "|" + tipoEvento;
    }

    /**
     * Contratos y eventos que, entre las propuestas dadas, ya tienen una comisión
     * vigente (misma regla que {@link #comisionYaGenerada}). Sirve al listado para
     * no ofrecer «Completar manualmente» donde se rechazaría: los servicios se
     * comprueban todos a la vez con un solo findAllById.
     */
    public Set<String> contratosConComisionVigente(Collection<McasPropuesta> propuestas) {
        List<McasPropuesta> conComision = propuestas.stream()
                .filter(p -> ESTADOS_CON_COMISION.contains(p.getEstado())).toList();
        if (conComision.isEmpty()) return Set.of();
        Set<Integer> idsServicio = conComision.stream().map(McasPropuesta::getIdServicioCreado)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Set<Integer> vigentes = idsServicio.isEmpty() ? Set.of() : servicioRepo.findAllById(idsServicio).stream()
                .map(Servicio::getId).collect(java.util.stream.Collectors.toSet());
        Set<String> claves = new HashSet<>();
        for (McasPropuesta p : conComision)
            if (p.getIdServicioCreado() != null && vigentes.contains(p.getIdServicioCreado()))
                claves.add(claveComision(p.getIdContrato(), p.getTipoEvento()));
        return claves;
    }

    /**
     * Una comisión aprobada cuenta mientras exista su servicio. Si se elimina, ya no cuenta
     * y MCAS la vuelve a proponer (la BD deja vacío id_servicio_creado: ON DELETE SET NULL).
     * Si solo se da de baja sigue contando: es lo que pasa cuando el contrato o el punto se
     * dan de baja y llega una retrocesión, y la comisión cobrada no se tiene que volver a
     * proponer (respuesta del cliente a la duda 17).
     */
    private boolean tieneServicio(McasPropuesta p) {
        return p.getIdServicioCreado() != null && servicioRepo.findById(p.getIdServicioCreado()).isPresent();
    }

    private String mensajeComisionExistente(McasPropuesta previa) {
        return "El contrato ya tiene una comisión aprobada (propuesta #" + previa.getId()
                + (previa.getIdServicioCreado() != null ? ", servicio #" + previa.getIdServicioCreado() : "")
                + "). Los cambios posteriores del contrato no generan otra comisión: "
                + "revise el servicio existente y ajústelo si procede.";
    }

    private void exigirSinComisionPrevia(McasPropuesta p) {
        comisionYaGenerada(p.getIdContrato(), p.getTipoEvento(), p.getId()).ifPresent(previa -> {
            throw new IllegalStateException(mensajeComisionExistente(previa));
        });
    }

    private void validarVigenciaPropuesta(McasPropuesta p, Contrato contrato, String usuario) {
        String hashActual = snapshots.capturar(contrato).hash();
        if (hashActual.equals(p.getHashContrato())) return;
        String anterior = p.getEstado();
        liberarClave(p, "stale");
        p.setEstado("DESACTUALIZADO");
        p.setActualizadoPor(usuario);
        p.setActualizadoEn(LocalDateTime.now());
        propuestaRepo.save(p);
        auditoria.registrar(p.getId(), p.getIdContrato(), "PROPUESTA_DESACTUALIZADA",
                anterior, "DESACTUALIZADO", Map.of("hash_actual", hashActual), usuario, null);
        throw new McasPropuestaDesactualizadaException(
                "El contrato ha cambiado desde el cálculo: recalcule la propuesta antes de aprobarla.");
    }

    private LocalDate fecha(java.util.Date valor, LocalDate porDefecto) {
        return valor == null ? porDefecto : api.aura.util.FechaNegocio.aLocalDate(valor);
    }

    /** Datos mínimos del contrato para calcular (también lo usa la simulación). */
    public List<String> camposFaltantes(Contrato c) {
        List<String> f = new ArrayList<>();
        if (c.getContrato() == null || c.getContrato().isBlank()) f.add("numero_contrato");
        if (c.getFechaInicio() == null) f.add("fecha_inicio");
        if (c.getMcasTipoPrecio() == null || !List.of("FIJO", "INDEXADO").contains(c.getMcasTipoPrecio().trim().toUpperCase()))
            f.add("tipo_precio_FIJO_o_INDEXADO");
        if (c.getMcasTipoSuministro() == null || !List.of("LUZ", "GAS").contains(c.getMcasTipoSuministro().trim().toUpperCase()))
            f.add("tipo_suministro_LUZ_o_GAS");
        if (c.getIdComercializadoraActual() == null || c.getIdComercializadoraActual() == 0) f.add("comercializadora");
        if (c.getConsumoAnual() != null && c.getConsumoAnual() < 0) f.add("consumo_anual_no_negativo");
        if (c.getMcasFeeEurMwh() != null && c.getMcasFeeEurMwh().signum() < 0) f.add("fee_no_negativo");
        if (c.getMcasFeeEurMwh() != null
                && (c.getMcasFeeUnidad() == null || c.getMcasFeeUnidad().isBlank())) f.add("unidad_fee");
        if (c.getMcasFeeEurMwh() != null && c.getMcasFeeUnidad() != null
                && !List.of("EUR_MWH", "€/MWH").contains(c.getMcasFeeUnidad().trim().toUpperCase()))
            f.add("unidad_fee_EUR_MWH");
        if (Boolean.TRUE.equals(c.getMcasEsRenovacion())
                && (c.getMcasTipoRenovacion() == null || c.getMcasTipoRenovacion().isBlank()))
            f.add("tipo_renovacion");
        if (c.getMcasIdProductoComercial() != null) {
            McasProductoComercial producto = productoRepo.findById(c.getMcasIdProductoComercial()).orElse(null);
            LocalDate fecha = fecha(c.getFechaInicio(), LocalDate.now());
            boolean incompatible = producto == null || !Boolean.TRUE.equals(producto.getActivo())
                    || !Objects.equals(producto.getIdComercializadora(), c.getIdComercializadoraActual())
                    || (producto.getIdGestora() != null && !Objects.equals(producto.getIdGestora(), c.getMcasIdGestora()))
                    || !igualIgnorandoMayusculas(producto.getTipoPrecio(), c.getMcasTipoPrecio())
                    || !igualIgnorandoMayusculas(producto.getTipoSuministro(), c.getMcasTipoSuministro())
                    || (producto.getActivoDesde() != null && fecha.isBefore(producto.getActivoDesde()))
                    || (producto.getActivoHasta() != null && fecha.isAfter(producto.getActivoHasta()));
            if (incompatible) f.add("producto_comercial_activo_y_compatible");
        }
        return f;
    }

    private boolean igualIgnorandoMayusculas(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private String clave(Contrato c, String hashContrato) {
        String tipo = Boolean.TRUE.equals(c.getMcasEsRenovacion()) ? "RENOVACION" : "NUEVO";
        return c.getId() + "|" + tipo + "|" + hashContrato;
    }

    /** Datos del contrato tal como los evalúa el motor (también lo usa la simulación). */
    public ContextoCalculo construirContexto(Contrato c, String corr) {
        BigDecimal kwh = c.getConsumoAnual() != null ? BigDecimal.valueOf(c.getConsumoAnual()) : null;
        BigDecimal mwh = kwh != null ? kwh.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP) : null;
        LocalDate fechaEf = fecha(c.getFechaInicio(), LocalDate.now());
        LocalDate ultimaRelacion = fecha(c.getMcasFechaUltimaRelacionComercializadora(), null);

        String codigoTarifa = resolverCodigoTarifa(c);
        McasProductoComercial producto = c.getMcasIdProductoComercial() != null
                ? productoRepo.findById(c.getMcasIdProductoComercial()).orElse(null) : null;
        String codigoProducto = producto != null ? producto.getCodigo() : null;

        // Potencia de cálculo: siempre la mayor contratada (P1–P6). Desde la reunión del
        // 04/11/2026 ya no se escribe a mano: un valor antiguo del contrato se ignora.
        BigDecimal potencia = mayorPotenciaContratada(c);
        String potenciaOrigen = potencia != null ? "MAYOR_CONTRATADA" : null;

        // Zona de comisión: por la provincia del punto de suministro (o su CUPS si la
        // comercializadora no tiene zonas). Lo que hubiera escrito en el contrato se ignora.
        McasZonasService.Resuelta zona = zonas.aplicar(c);

        return ContextoCalculo.builder()
                .idContrato(c.getId())
                .idComercializadora(c.getIdComercializadoraActual())
                .idGestora(c.getMcasIdGestora())
                .tipoSuministro(c.getMcasTipoSuministro())
                .segmento(c.getMcasSegmento())
                .esRenovacion(c.getMcasEsRenovacion())
                .tipoRenovacion(c.getMcasTipoRenovacion())
                .tipoPrecio(c.getMcasTipoPrecio())
                .zonaComision(zona.zona())
                .motivoSinZona(zona.motivo())
                .idTarifaLuz(c.getIdTarifaLuz() != 0 ? c.getIdTarifaLuz() : null)
                .idTarifaGas(c.getIdTarifaGas() != 0 ? c.getIdTarifaGas() : null)
                .codigoTarifaAcceso(codigoTarifa)
                .idProductoComercial(c.getMcasIdProductoComercial())
                .codigoProductoComercial(codigoProducto)
                .nombreProductoComercial(producto != null ? producto.getNombre() : null)
                .potenciaCalculoKw(potencia)
                .potenciaOrigen(potenciaOrigen)
                .consumoAnualKwh(kwh)
                .consumoAnualMwh(mwh)
                .feeEurMwh(c.getMcasFeeEurMwh())
                .numeroPagos(pagosDeLaComercializadora(c))
                .fechaEfectiva(fechaEf)
                .altaNueva(c.getMcasAltaNueva())
                .fechaUltimaRelacionComercializadora(ultimaRelacion)
                .mesesDesdeUltimaRelacion(mesesEntre(ultimaRelacion, fechaEf))
                .correlacionId(corr)
                .build();
    }

    /**
     * Meses completos entre el fin de la última relación con la comercializadora y la fecha de
     * inicio (15/05/2025 a 13/02/2026: 8). Null sin relación anterior; 0 si se solapan.
     */
    static Integer mesesEntre(LocalDate ultimaRelacion, LocalDate inicio) {
        if (ultimaRelacion == null || inicio == null) return null;
        return (int) Math.max(0, java.time.temporal.ChronoUnit.MONTHS.between(ultimaRelacion, inicio));
    }

    /** Mayor potencia contratada (P1–P6) mayor que cero, o null si no hay ninguna. */
    private BigDecimal mayorPotenciaContratada(Contrato c) {
        return java.util.stream.Stream.of(c.getPotencia1(), c.getPotencia2(), c.getPotencia3(),
                        c.getPotencia4(), c.getPotencia5(), c.getPotencia6())
                .filter(Objects::nonNull).filter(p -> p > 0)
                .map(BigDecimal::valueOf).max(BigDecimal::compareTo).orElse(null);
    }

    /**
     * Tarifa de acceso con la que se evalúan las reglas. En un contrato de gas va la de
     * gas (igual que en el formulario, mcasCamposContrato.campoDeClave): esos contratos
     * guardan también el id de la tarifa de luz «Sin Tarifa», que antes ganaba siempre,
     * y ninguna regla de gas por tarifa podía coincidir.
     */
    public String resolverCodigoTarifa(Contrato c) {
        String tipo = c.getMcasTipoSuministro() != null ? c.getMcasTipoSuministro().trim() : "";
        // Con el suministro indicado solo vale su propia tarifa: un contrato de luz sin
        // tarifa de luz cogía la «Sin Tarifa» de gas y acababa en revisión manual sin
        // decir que faltaba la tarifa (ahora pide el dato, como cualquier otro que falte)
        if ("GAS".equalsIgnoreCase(tipo))
            return c.getIdTarifaGas() != 0 ? nombreTarifaGas(c.getIdTarifaGas()) : null;
        if ("LUZ".equalsIgnoreCase(tipo))
            return c.getIdTarifaLuz() != 0 ? nombreTarifaLuz(c.getIdTarifaLuz()) : null;
        if (c.getIdTarifaLuz() != 0)
            return nombreTarifaLuz(c.getIdTarifaLuz());
        if (c.getIdTarifaGas() != 0)
            return nombreTarifaGas(c.getIdTarifaGas());
        return null;
    }

    private String nombreTarifaLuz(int id) {
        return tarifasLuzRepo.findById(id).map(t -> t.getNombre()).orElse(null);
    }

    private String nombreTarifaGas(int id) {
        return tarifasGasRepo.findById(id).map(t -> t.getNombre()).orElse(null);
    }

    /**
     * Propuesta que no pasa por el motor (faltan datos básicos, interruptores
     * apagados o comisión ya aprobada). La advertencia va en texto para el
     * usuario; la causa técnica y los datos que faltan quedan en la traza (con las
     * mismas claves que usa el motor) y en la auditoría.
     */
    private McasPropuesta crearPropuesta(Contrato c, String estado, String causa, List<String> campos,
                                          String corr, String usuario,
                                          McasContratoSnapshotService.Snapshot snapshot) {
        return crearPropuesta(c, estado, causa, campos, Map.of(), corr, usuario, snapshot);
    }

    private McasPropuesta crearPropuesta(Contrato c, String estado, String causa, List<String> campos,
                                          Map<String, Object> trazaExtra, String corr, String usuario,
                                          McasContratoSnapshotService.Snapshot snapshot) {
        String clave = clave(c, snapshot.hash());
        Optional<McasPropuesta> ex = propuestaActivaPorClave(clave, usuario, corr);
        if (ex.isPresent())
            return ex.get();
        // Sin pasar por el motor la propuesta también muestra la retrocesión potencial
        // (sin comercializadora no hay política) y precarga los pagos del contrato
        // para completarla a mano.
        List<String> advertencias = new ArrayList<>(List.of(McasMensajes.paraOperador(causa)));
        Integer idRetrocesion = seleccionarRetrocesion(construirContexto(c, corr), advertencias);
        Map<String, Object> traza = new LinkedHashMap<>();
        traza.put("REQUIERE_DATOS".equals(estado) ? "requiere_datos" : "revision_manual", causa);
        if (!campos.isEmpty()) traza.put("campos_faltantes", campos);
        traza.putAll(trazaExtra);
        McasPropuesta p = propuestaRepo.save(McasPropuesta.builder()
                 .idContrato(c.getId())
                .hashContrato(snapshot.hash()).instantaneaJson(snapshot.json())
                .tipoEvento(tipoEvento(c))
                .claveIdempotencia(clave).estado(estado)
                .idPoliticaRetrocesion(idRetrocesion)
                .numeroPagos(pagosDeLaComercializadora(c))
                .trazaCalculoJson(toJson(traza))
                .advertenciasJson(toJson(advertencias)).creadoPor(usuario).build());
        auditoria.registrar(p.getId(), c.getId(), "PROPUESTA_CREADA", null, estado,
                Map.of("causa", causa), usuario, corr);
        return p;
    }

    private McasPropuesta buildPropuesta(Contrato c, ContextoCalculo ctx,
                                          ResultadoCalculo res, String clave, String usuario,
                                          McasContratoSnapshotService.Snapshot snapshot) {
        String estado = switch (res.getResultado()) {
            case CALCULADO -> "PENDIENTE_VALIDACION";
            case REQUIERE_REVISION_MANUAL -> "REQUIERE_REVISION_MANUAL";
            case NO_COMISIONABLE -> "FINALIZADO_NO_COMISIONABLE";
            case ERROR_CALCULO -> "ERROR_CALCULO";
            case REQUIERE_DATOS -> "REQUIERE_DATOS";
        };
        String tipo = tipoEvento(c);
        // La primera cuota va en la fecha del primer cobro (condiciones de cobro de la
        // comercializadora; sin configurar, la fecha de inicio del contrato).
        LocalDate fechaPrimeraAplicacion = condicionesCobro.fechasCobro(
                ctx.getIdComercializadora(), ctx.getIdGestora(), ctx.getFechaEfectiva(), 1).get(0);
        List<String> advertencias = new ArrayList<>(
                res.getAdvertencias() != null ? res.getAdvertencias() : List.of());
        Integer idRetrocesion = seleccionarRetrocesion(ctx, advertencias);
        return McasPropuesta.builder()
                .idContrato(c.getId()).tipoEvento(tipo).claveIdempotencia(clave)
                .estado(estado).hashContrato(snapshot.hash()).instantaneaJson(snapshot.json())
                .idConjuntoReglas(res.getIdConjuntoReglas())
                .idPoliticaRetrocesion(idRetrocesion)
                .datosNormalizadosJson(toJson(Map.of(
                        "consumo_mwh", ctx.getConsumoAnualMwh() != null ? ctx.getConsumoAnualMwh() : "null",
                        "tarifa_acceso", ctx.getCodigoTarifaAcceso() != null ? ctx.getCodigoTarifaAcceso() : "null",
                        "codigo_producto", ctx.getCodigoProductoComercial() != null ? ctx.getCodigoProductoComercial() : "null")))
                .trazaCalculoJson(res.getTrazaJson())
                .importeCalculado(res.getImporteTotal())
                .importeDefinitivo(res.getImporteTotal())
                // Sin importe calculado el motor no resuelve los pagos: los del contrato,
                // para que «Completar manualmente» no cree el servicio en un solo pago.
                .numeroPagos(res.getNumeroPagos() != null ? res.getNumeroPagos() : pagosDeLaComercializadora(c))
                .fechaPrimeraAplicacion(fechaPrimeraAplicacion)
                .advertenciasJson(advertencias.isEmpty() ? null : toJson(advertencias))
                .creadoPor(usuario).actualizadoPor(usuario).actualizadoEn(LocalDateTime.now())
                .build();
    }

    /**
     * En el alta la politica de retrocesion no calcula ni descuenta nada: se asocia
     * siempre para mostrar la «retrocesión potencial» y es la que usa despues
     * McasRetrocesionService (con FLAG_RETROCESION encendido) si hay una baja antes del año. Si
     * esta mal configurada (dos politicas con la misma especificidad) se deja sin
     * asignar y se anota como advertencia, en vez de tumbar toda la propuesta y
     * dejar el contrato sin ninguna comision que revisar.
     */
    private Integer seleccionarRetrocesion(ContextoCalculo ctx, List<String> advertencias) {
        try {
            return retrocesionPolicies.seleccionar(ctx).orElse(null);
        } catch (org.springframework.dao.DataAccessException | jakarta.persistence.PersistenceException e) {
            // Un fallo de la BD no es una política mal configurada (ver MotorMcas)
            throw e;
        } catch (RuntimeException e) {
            // Lo técnico, al log; al usuario, el aviso en texto humano
            log.warn("MCAS contrato={} sin politica de retrocesion: {}", ctx.getIdContrato(), e.getMessage(), e);
            advertencias.add(McasMensajes.paraOperador("Sin politica de retrocesion asignada: " + e.getMessage()));
            return null;
        }
    }

    private void guardarLineas(McasPropuesta p, ResultadoCalculo res) {
        int orden = 1;
        for (ResultadoCalculo.LineaResultado l : res.getLineas()) {
            lineaRepo.save(McasLineaPropuesta.builder()
                    .idPropuesta(p.getId()).tipoLinea(l.getTipoLinea())
                    .descripcion(l.getDescripcion()).metodo(l.getMetodo())
                    .valorBase(l.getValorBase()).valorTasa(l.getValorTasa())
                    .valorPorcentaje(l.getValorPorcentaje())
                    .importeCalculado(l.getImporte()).importeDefinitivo(l.getImporte())
                    .idRegla(l.getIdRegla()).orden(orden++).build());
        }
    }

    private void guardarCalendario(Contrato c, McasPropuesta p, ResultadoCalculo res) {
        int n = res.getNumeroPagos() != null ? res.getNumeroPagos() : 1;
        reconstruirCalendario(c, p, res.getImporteTotal(), n);
    }

    /** Cuotas de la comisión con las fechas de cobro de la comercializadora del contrato. */
    private void reconstruirCalendario(Contrato c, McasPropuesta p, BigDecimal importe, int numeroPagos) {
        calendarioRepo.deleteByIdPropuesta(p.getId());
        List<BigDecimal> cuotas = motor.buildPagos(importe, numeroPagos);
        List<LocalDate> fechas = fechasCobro(c, cuotas.size());
        p.setFechaPrimeraAplicacion(fechas.get(0));
        for (int i = 0; i < cuotas.size(); i++) {
            calendarioRepo.save(McasCalendarioPago.builder()
                    .idPropuesta(p.getId()).numeroPago(i + 1)
                    .fechaPago(fechas.get(i))
                    .importe(cuotas.get(i)).build());
        }
    }

    private List<LocalDate> fechasCobro(Contrato c, int cuotas) {
        return condicionesCobro.fechasCobro(c.getIdComercializadoraActual(), c.getMcasIdGestora(),
                fecha(c.getFechaInicio(), null), cuotas);
    }

    private void actualizarLineasDefinitivas(McasPropuesta p, BigDecimal totalDefinitivo) {
        List<McasLineaPropuesta> lineas = lineaRepo.findByIdPropuestaOrderByOrdenAsc(p.getId());
        if (lineas.isEmpty()) return;
        BigDecimal totalCalculado = lineas.stream().map(McasLineaPropuesta::getImporteCalculado)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int i = 0; i < lineas.size(); i++) {
            McasLineaPropuesta linea = lineas.get(i);
            BigDecimal definitivo;
            if (i == lineas.size() - 1) definitivo = totalDefinitivo.subtract(acumulado);
            else if (totalCalculado.signum() == 0) definitivo = BigDecimal.ZERO;
            else definitivo = totalDefinitivo.multiply(linea.getImporteCalculado())
                    .divide(totalCalculado, 6, RoundingMode.HALF_UP);
            linea.setImporteDefinitivo(definitivo);
            acumulado = acumulado.add(definitivo);
            lineaRepo.save(linea);
        }
    }

    private String datosEdicionJson(McasPropuesta p, BigDecimal importe, Integer pagos,
                                     String overrideJson, String motivo) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("importe_calculado", p.getImporteCalculado());
        datos.put("importe_definitivo_anterior", p.getImporteDefinitivo());
        datos.put("importe_definitivo_nuevo", importe != null ? importe : p.getImporteDefinitivo());
        datos.put("numero_pagos_anterior", p.getNumeroPagos());
        datos.put("numero_pagos_nuevo", pagos != null ? pagos : p.getNumeroPagos());
        datos.put("motivo", motivo.trim());
        if (overrideJson != null && !overrideJson.isBlank()) {
            try {
                var override = mapper.readTree(overrideJson);
                if (!override.isObject()) throw new IllegalArgumentException("overrideJson debe ser un objeto JSON");
                datos.put("override", override);
            } catch (IllegalArgumentException e) { throw e; }
            catch (Exception e) { throw new IllegalArgumentException("overrideJson no es JSON valido"); }
        }
        return toJson(datos);
    }

    private Servicio crearServicio(McasPropuesta p, Contrato c, String usuario, boolean editado) {
        BigDecimal imp = p.getImporteDefinitivo() != null ? p.getImporteDefinitivo() : p.getImporteCalculado();
        String desc = "MCAS" + (editado ? " [EDITADO]" : "") + " - "
                + (c.getMcasTipoPrecio() != null ? c.getMcasTipoPrecio() : "")
                + " Propuesta #" + p.getId();

        String cups = null;
        if (c.getPuntoSuministro() != null) {
            cups = c.getPuntoSuministro().getCUPSluz();
            if (cups == null || cups.isBlank()) cups = c.getPuntoSuministro().getCUPSgas();
        }

        List<McasCalendarioPago> pagos = calendarioRepo.findByIdPropuestaOrderByNumeroPagoAsc(p.getId());
        if (pagos.size() > 12)
            throw new IllegalStateException("La comisión tiene más de 12 pagos y un servicio admite como mucho 12.");
        int cuotas = pagos.isEmpty() ? 1 : pagos.size();
        // El informe «Servicios pendientes de pago» cruza servicios_pagos con
        // tbpago_plazos: un servicio sin modalidad no aparece nunca en el.
        TBPagoPlazos plazos = pagoPlazosRepo.findFirstByNumeroCuotas(cuotas)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe una modalidad de pago de " + cuotas + " cuota" + (cuotas == 1 ? "" : "s")
                        + ": créela en Administración › Pago Plazos antes de aprobar la comisión."));

        Servicio s = new Servicio();
        s.setFecha(new java.sql.Date(System.currentTimeMillis()));
        s.setCliente(c.getCliente());
        s.setPuntoSuministro(c.getPuntoSuministro());
        s.setDescripcion(desc);
        s.setIdComercializadora(c.getIdComercializadoraActual());
        s.setIdGestora(c.getMcasIdGestora());
        // Servicio.idProducto referencia el catálogo de servicios (TBProductos), no
        // el producto comercial MCAS del contrato.
        s.setIdProducto(productosServicioRepo
                .findFirstByNombreContainingIgnoreCaseOrderByIdAsc(PRODUCTO_SERVICIO_COMISION)
                .map(TBProductos::getId).orElse(null));
        s.setIdPagoPlazos(plazos.getId());
        s.setCups(cups);
        s.setImporte(imp != null ? imp.doubleValue() : 0.0);
        s.setImporteFinal(imp != null ? imp.doubleValue() : 0.0);
        s.setDescuento(0.0);
        // El servicio dura lo que el contrato; cada cuota lleva su fecha de cobro
        if (c.getFechaInicio() != null)
            s.setFechaInicioServicio(new java.sql.Date(c.getFechaInicio().getTime()));
        else if (p.getFechaPrimeraAplicacion() != null)
            s.setFechaInicioServicio(java.sql.Date.valueOf(p.getFechaPrimeraAplicacion()));
        if (c.getFechaFin() != null)
            s.setFechaFinServicio(new java.sql.Date(c.getFechaFin().getTime()));
        s.setObservaciones("Comisión generada por MCAS. Propuesta #" + p.getId() + ". "
                + origenImporte(p) + ". Aprobada por: " + usuario + ".");
        s = servicioRepo.save(s);
        crearServiciosPagos(s, pagos, plazos, imp);
        return s;
    }

    /**
     * Regla que generó el importe, leída de la traza del cálculo, para que el
     * servicio permita recuperar regla y versión (v2 §9). Si el motor no calculó
     * importe, lo introdujo el operador.
     */
    private String origenImporte(McasPropuesta p) {
        if (p.getImporteCalculado() != null && p.getTrazaCalculoJson() != null) {
            try {
                var traza = mapper.readTree(p.getTrazaCalculoJson());
                // Nombres legibles si los hay (traza nueva); si no, los códigos
                String regla = textoTraza(traza, "regla_nombre", "regla");
                if (regla != null) {
                    String conjunto = textoTraza(traza, "conjunto_nombre", "conjunto");
                    String version = traza.path("conjunto_version").asText(null);
                    return "Regla «" + regla + "»" + (conjunto != null
                            ? " de la tabla de comisiones «" + conjunto + "»" + (version != null ? " (versión " + version + ")" : "")
                            : "");
                }
            } catch (Exception e) {
                log.warn("MCAS propuesta={} traza ilegible al crear el servicio: {}", p.getId(), e.getMessage());
            }
        }
        return "Importe introducido manualmente";
    }

    /** Primer texto no vacío de la traza entre las claves dadas. */
    private static String textoTraza(com.fasterxml.jackson.databind.JsonNode traza, String... claves) {
        for (String clave : claves) {
            String valor = traza.path(clave).asText(null);
            if (valor != null && !valor.isBlank()) return valor.trim();
        }
        return null;
    }

    private void crearServiciosPagos(Servicio s, List<McasCalendarioPago> pagos, TBPagoPlazos plazos,
                                     BigDecimal imp) {
        ServiciosPagos sp = new ServiciosPagos();
            sp.setServicio(s);
            sp.setImporte(imp != null ? imp.doubleValue() : 0.0);
            sp.setImporteFinal(imp != null ? imp.doubleValue() : 0.0);
            sp.setDescuento(0.0);
            sp.setPagoPlazos(plazos);
            for (int i = 0; i < Math.min(pagos.size(), 12); i++) {
                McasCalendarioPago pg = pagos.get(i);
                java.util.Date f = pg.getFechaPago() != null ? java.sql.Date.valueOf(pg.getFechaPago()) : null;
                double amt = pg.getImporte().doubleValue();
                switch (i) {
                    case 0 -> { sp.setPrimerPagoFecha(f); sp.setPrimerPagoImporte(amt); sp.setPrimerPagoPagado(false); }
                    case 1 -> { sp.setSegundoPagoFecha(f); sp.setSegundoPagoImporte(amt); sp.setSegundoPagoPagado(false); }
                    case 2 -> { sp.setTercerPagoFecha(f); sp.setTercerPagoImporte(amt); sp.setTercerPagoPagado(false); }
                    case 3 -> { sp.setCuartoPagoFecha(f); sp.setCuartoPagoImporte(amt); sp.setCuartoPagoPagado(false); }
                    case 4 -> { sp.setQuintoPagoFecha(f); sp.setQuintoPagoImporte(amt); sp.setQuintoPagoPagado(false); }
                    case 5 -> { sp.setSextoPagoFecha(f); sp.setSextoPagoImporte(amt); sp.setSextoPagoPagado(false); }
                    case 6 -> { sp.setSeptimoPagoFecha(f); sp.setSeptimoPagoImporte(amt); sp.setSeptimoPagoPagado(false); }
                    case 7 -> { sp.setOctavoPagoFecha(f); sp.setOctavoPagoImporte(amt); sp.setOctavoPagoPagado(false); }
                    case 8 -> { sp.setNovenoPagoFecha(f); sp.setNovenoPagoImporte(amt); sp.setNovenoPagoPagado(false); }
                    case 9 -> { sp.setDecimoPagoFecha(f); sp.setDecimoPagoImporte(amt); sp.setDecimoPagoPagado(false); }
                    case 10 -> { sp.setDecimoprimerPagoFecha(f); sp.setDecimoprimerPagoImporte(amt); sp.setDecimoprimerPagoPagado(false); }
                    case 11 -> { sp.setDecimosegundoPagoFecha(f); sp.setDecimosegundoPagoImporte(amt); sp.setDecimosegundoPagoPagado(false); }
                }
            }
            if (pagos.isEmpty() && imp != null) { sp.setPrimerPagoImporte(imp.doubleValue()); sp.setPrimerPagoPagado(false); }
        serviciosPagosRepo.save(sp);
    }

    // ============================================================
    // CONTRATO BORRADO Y CAMBIOS DE UN CONTRATO YA COMISIONADO
    // ============================================================

    /**
     * Borra las comisiones de un contrato que se va a borrar: sus propuestas (con su desglose
     * y calendario) y los servicios de comisión que crearon, con su plan de pagos. Borrar un
     * contrato es que no llegó a hacerse, así que su comisión tampoco existe (respuesta del
     * cliente a la duda 17). La auditoría se conserva, con una entrada que lo explica.
     * Una baja del contrato o del punto es otra cosa: no pasa por aquí y la comisión se queda.
     */
    @Transactional
    public void eliminarComisionesDelContrato(Integer idContrato) {
        List<McasPropuesta> propuestas = propuestaRepo.findByIdContratoOrderByCreadoEnDesc(idContrato);
        if (propuestas.isEmpty()) return;
        String usuario = usuario();
        List<Integer> servicios = propuestas.stream().map(McasPropuesta::getIdServicioCreado)
                .filter(Objects::nonNull).distinct().toList();
        for (Integer idServicio : servicios) {
            serviciosPagosRepo.findByServicioId(idServicio).ifPresent(serviciosPagosRepo::delete);
            if (servicioRepo.existsById(idServicio)) servicioRepo.deleteById(idServicio);
        }
        for (McasPropuesta p : propuestas) {
            calendarioRepo.deleteByIdPropuesta(p.getId());
            lineaRepo.deleteByIdPropuesta(p.getId());
        }
        propuestaRepo.deleteAll(propuestas);
        auditoria.registrar(null, idContrato, "CONTRATO_ELIMINADO", null, null,
                Map.of("propuestas_borradas", propuestas.size(), "servicios_borrados", servicios), usuario, null);
        log.info("MCAS contrato={} borrado: {} propuestas y servicios {} eliminados", idContrato, propuestas.size(), servicios);
    }

    private static final String CLAVE_FECHA_INICIO = "fecha_inicio";

    /**
     * Datos de la instantánea actual que difieren de los de la comisión aprobada. Solo se
     * comparan las claves de la instantánea de ahora: las que se quitaron después (p. ej. la
     * fecha fin o el nº de pagos del contrato) ya no influyen y no cuentan como cambio. Si la
     * instantánea aprobada no se puede leer, se da por cambiada entera (revisión a mano).
     */
    private Set<String> cambiosRespectoA(McasPropuesta aprobada, McasContratoSnapshotService.Snapshot actual) {
        if (actual.hash().equals(aprobada.getHashContrato())) return Set.of();
        try {
            Map<String, Object> antes = mapper.readValue(aprobada.getInstantaneaJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
            Map<String, Object> ahora = mapper.readValue(actual.json(),
                    new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
            Set<String> cambios = new TreeSet<>();
            for (Map.Entry<String, Object> dato : ahora.entrySet())
                if (!antes.containsKey(dato.getKey()) || !Objects.equals(String.valueOf(antes.get(dato.getKey())),
                        String.valueOf(dato.getValue())))
                    cambios.add(dato.getKey());
            return cambios;
        } catch (Exception e) {
            log.warn("MCAS propuesta={} instantánea ilegible: {}", aprobada.getId(), e.getMessage());
            return Set.of("instantanea");
        }
    }

    /** Pagos de cada cuota de ServiciosPagos, del 1 al 12 (las columnas se llaman así). */
    private static final List<String> CUOTAS_SERVICIO = List.of("primer", "segundo", "tercer", "cuarto", "quinto",
            "sexto", "septimo", "octavo", "noveno", "decimo", "decimoprimer", "decimosegundo");

    /**
     * Solo ha cambiado la fecha de inicio de un contrato con la comisión aprobada: las cuotas
     * que aún no se han cobrado pasan a las fechas de cobro de la nueva fecha de inicio, el
     * servicio empieza el nuevo día y la comisión queda al día con el contrato, sin abrir otra
     * propuesta ni avisos. No se tocan las cobradas ni las que se cambiaron a mano en el
     * servicio (duda 13: cuotas 2 y 3 con huecos): solo se mueve la que sigue en la fecha que
     * le tocaba con la fecha de inicio anterior.
     */
    private McasPropuesta moverCobros(McasPropuesta aprobada, Contrato c,
                                      McasContratoSnapshotService.Snapshot snapshot, String usuario, String corr) {
        Servicio servicio = servicioRepo.findById(aprobada.getIdServicioCreado()).orElseThrow();
        List<McasCalendarioPago> calendario = calendarioRepo.findByIdPropuestaOrderByNumeroPagoAsc(aprobada.getId());
        int cuotas = Math.max(1, Math.max(calendario.size(),
                aprobada.getNumeroPagos() != null ? aprobada.getNumeroPagos() : 1));
        List<LocalDate> fechas = fechasCobro(c, Math.min(cuotas, CUOTAS_SERVICIO.size()));
        LocalDate inicioAnteriorAprobado = fechaInicioDeLaInstantanea(aprobada);
        List<LocalDate> fechasAnteriores = condicionesCobro.fechasCobro(c.getIdComercializadoraActual(),
                c.getMcasIdGestora(), inicioAnteriorAprobado, fechas.size());
        List<Integer> aMano = new ArrayList<>();

        List<Integer> movidas = new ArrayList<>();
        Optional<ServiciosPagos> plan = serviciosPagosRepo.findByServicioId(servicio.getId());
        plan.ifPresent(sp -> {
            org.springframework.beans.BeanWrapper cuota = new org.springframework.beans.BeanWrapperImpl(sp);
            for (int i = 0; i < fechas.size(); i++) {
                String prefijo = CUOTAS_SERVICIO.get(i) + "Pago";
                if (Boolean.TRUE.equals(cuota.getPropertyValue(prefijo + "Pagado"))) continue;
                if (cuota.getPropertyValue(prefijo + "Importe") == null && i > 0) continue;
                LocalDate actual = api.aura.util.FechaNegocio.aLocalDate((java.util.Date) cuota.getPropertyValue(prefijo + "Fecha"));
                if (actual != null && !actual.equals(fechasAnteriores.get(i))) { aMano.add(i + 1); continue; }
                cuota.setPropertyValue(prefijo + "Fecha", fechas.get(i) != null ? java.sql.Date.valueOf(fechas.get(i)) : null);
                movidas.add(i + 1);
            }
            serviciosPagosRepo.save(sp);
        });
        for (McasCalendarioPago pago : calendario) {
            int i = pago.getNumeroPago() - 1;
            if (i >= 0 && i < fechas.size() && (plan.isEmpty() || movidas.contains(pago.getNumeroPago())))
                pago.setFechaPago(fechas.get(i));
        }
        calendarioRepo.saveAll(calendario);

        java.util.Date inicioAnterior = servicio.getFechaInicioServicio();
        if (c.getFechaInicio() != null) servicio.setFechaInicioServicio(new java.sql.Date(c.getFechaInicio().getTime()));
        servicioRepo.save(servicio);

        if (!fechas.isEmpty() && (plan.isEmpty() || movidas.contains(1))) aprobada.setFechaPrimeraAplicacion(fechas.get(0));
        // Su clave era la de la instantánea anterior: si la fecha volviera atrás, la encontraría
        // por clave y no movería las cuotas. Sin clave, cada cambio se compara con la instantánea.
        liberarClave(aprobada, "cobros");
        aprobada.setHashContrato(snapshot.hash());
        aprobada.setInstantaneaJson(snapshot.json());
        aprobada.setActualizadoPor(usuario);
        aprobada.setActualizadoEn(LocalDateTime.now());
        propuestaRepo.save(aprobada);
        auditoria.registrar(aprobada.getId(), c.getId(), "COBROS_MOVIDOS_POR_FECHA_INICIO", aprobada.getEstado(),
                aprobada.getEstado(), Map.of(
                        "fecha_inicio_anterior", String.valueOf(inicioAnterior),
                        "fecha_inicio_nueva", String.valueOf(fecha(c.getFechaInicio(), null)),
                        "cuotas_movidas", movidas,
                        "cuotas_cambiadas_a_mano", aMano,
                        "id_servicio", servicio.getId()), usuario, corr);
        log.info("MCAS contrato={} propuesta={}: cuotas {} movidas por cambio de fecha de inicio",
                c.getId(), aprobada.getId(), movidas);
        return aprobada;
    }

    /** Fecha de inicio guardada en la instantánea de la propuesta (la que había al aprobar o al último traslado). */
    private LocalDate fechaInicioDeLaInstantanea(McasPropuesta p) {
        try {
            String texto = mapper.readTree(p.getInstantaneaJson()).path(CLAVE_FECHA_INICIO).asText(null);
            return texto == null || texto.isBlank() || "null".equals(texto) ? null : LocalDate.parse(texto.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            log.error("MCAS: no se pudo convertir a JSON un {}", o == null ? "null" : o.getClass().getSimpleName(), e);
            return "{}";
        }
    }
}
