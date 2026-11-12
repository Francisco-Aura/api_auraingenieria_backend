package api.aura.service.mcas;

import api.aura.config.AuraUserHolder;
import api.aura.dto.mcas.McasIncidenciaDTO;
import api.aura.dto.mcas.McasResultadoLoteDTO;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasValorRango;
import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasPropuestaRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.service.ModuloConfiguracionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;

/**
 * Ciclo de vida de una tabla de comisiones (conjunto de reglas) para un
 * administrador no técnico: crear con código automático, volver a borrador,
 * nueva versión, comprobar y activar (validar + cerrar la vigencia anterior +
 * publicar en una sola transacción) y activar en lote.
 *
 * <p>La fase indexada sigue una única política, {@link #motivoFaseBloqueada}: una
 * tabla que no es de precio fijo solo se valida, publica o activa con el
 * interruptor {@code MCAS_INDEXED_ENABLED} encendido.</p>
 */
@Service
public class McasConjuntoService {

    private static final Logger log = LoggerFactory.getLogger(McasConjuntoService.class);

    /** Texto del contrato de API (A8). */
    public static final String FASE_INDEXADA_DESACTIVADA =
            "Las tablas de precio indexado no se pueden activar con el interruptor «Indexado» apagado: "
                    + "enciéndalo en Configuración MCAS, en «Interruptores del módulo».";

    /** Propuestas que un recálculo cambiaría (las cerradas o en corrección manual no). */
    private static final List<String> PROPUESTAS_PENDIENTES = List.of("PENDIENTE_CALCULO", "PENDIENTE_VALIDACION",
            "REQUIERE_DATOS", "REQUIERE_REVISION_MANUAL", "ERROR_CALCULO");
    /** Propuestas sin tabla que la nueva tabla podría calcular al recalcularlas. */
    private static final List<String> PROPUESTAS_SIN_TABLA = List.of("REQUIERE_REVISION_MANUAL", "REQUIERE_DATOS",
            "PENDIENTE_VALIDACION");
    private static final int MAX_LOTE = 200;
    private static final int MAX_CODIGO = 100;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_CODIGO = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FECHA_VERSION = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyy-MM");

    private final McasConjuntoReglasRepository conjuntoRepo;
    private final McasReglaComisionRepository reglaRepo;
    private final McasValorRangoRepository rangoRepo;
    private final McasPropuestaRepository propuestaRepo;
    private final TBComercializadorasRepository comercializadoraRepo;
    private final McasRuleSetValidator validator;
    private final ModuloConfiguracionService flags;
    private final TransactionTemplate transaccion;

    @PersistenceContext
    private EntityManager em;

    public McasConjuntoService(McasConjuntoReglasRepository conjuntoRepo, McasReglaComisionRepository reglaRepo,
                               McasValorRangoRepository rangoRepo, McasPropuestaRepository propuestaRepo,
                               TBComercializadorasRepository comercializadoraRepo, McasRuleSetValidator validator,
                               ModuloConfiguracionService flags, PlatformTransactionManager transacciones) {
        this.conjuntoRepo = conjuntoRepo;
        this.reglaRepo = reglaRepo;
        this.rangoRepo = rangoRepo;
        this.propuestaRepo = propuestaRepo;
        this.comercializadoraRepo = comercializadoraRepo;
        this.validator = validator;
        this.flags = flags;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /** Resultado de «Comprobar» antes de activar: nada se modifica. */
    public record Comprobacion(boolean puedeActivar, List<McasIncidenciaDTO> incidencias,
                               List<String> cambios, List<String> avisos) {}

    // ============================================================
    // A8 · Fase indexada
    // ============================================================

    /**
     * Motivo por el que la fase de la tabla impide validarla, publicarla o
     * activarla; {@code null} si se permite. Hoy solo la fase indexada (o «ambos»)
     * con el interruptor Indexado apagado.
     */
    public String motivoFaseBloqueada(McasConjuntoReglas conjunto) {
        if (conjunto.getFasePrecio() != null && !"FIJO".equals(conjunto.getFasePrecio())
                && !flags.estaActivo(McasProposalService.FLAG_INDEXADO))
            return FASE_INDEXADA_DESACTIVADA;
        return null;
    }

    // ============================================================
    // C1 · Crear con nombre y código automático
    // ============================================================

    /** Crea la tabla en BORRADOR; si no trae código, lo genera (COMERCIALIZADORA-FASE-yyyyMMdd). */
    @Transactional
    public McasConjuntoReglas crear(McasConjuntoReglas datos) {
        if (datos.getIdComercializadora() == null)
            throw new IllegalArgumentException("Elija la comercializadora de la tabla.");
        if (datos.getVigenciaDesde() == null)
            throw new IllegalArgumentException("Indique desde qué fecha se aplica la tabla.");
        datos.setId(null);
        datos.setEstado("BORRADOR");
        datos.setFechaCreacion(LocalDateTime.now());
        datos.setCreadoPor(AuraUserHolder.get());
        datos.setAprobadoPor(null);
        datos.setFechaAprobacion(null);
        datos.setIdConjuntoOrigen(null);
        if (datos.getFasePrecio() == null || datos.getFasePrecio().isBlank()) datos.setFasePrecio("FIJO");
        if (datos.getVersion() == null || datos.getVersion().isBlank())
            datos.setVersion(datos.getVigenciaDesde().format(FECHA_VERSION));
        datos.setNombre(limpio(datos.getNombre()));
        datos.setDescripcion(limpio(datos.getDescripcion()));
        datos.setCodigo(vacio(datos.getCodigo()) ? codigoConjunto(datos) : datos.getCodigo().trim());
        return conjuntoRepo.save(datos);
    }

    /** Añade una regla a una tabla en borrador; sin código, lo genera a partir del nombre. */
    @Transactional
    public McasReglaComision crearRegla(Integer idConjunto, McasReglaComision regla) {
        McasConjuntoReglas conjunto = conjunto(idConjunto);
        if (!"BORRADOR".equals(conjunto.getEstado()))
            throw new IllegalStateException("Solo se pueden añadir reglas a una tabla en borrador; "
                    + "cree una nueva versión para cambiarla.");
        if (vacio(regla.getMetodoCalculo()))
            throw new IllegalArgumentException("Elija el método de cálculo de la regla.");
        regla.setId(null);
        regla.setIdConjuntoReglas(idConjunto);
        if (regla.getActivo() == null) regla.setActivo(true);
        if (regla.getPrioridad() == null) regla.setPrioridad(100);
        regla.setNombre(limpio(regla.getNombre()));
        regla.setDescripcion(limpio(regla.getDescripcion()));
        regla.setCodigo(vacio(regla.getCodigo()) ? codigoRegla(idConjunto, regla.getNombre()) : regla.getCodigo().trim());
        return reglaRepo.save(regla);
    }

    /** Código libre «ENDESA-FIJO-20261001» (con «-2», «-3»… si ya existe). */
    public String codigoConjunto(McasConjuntoReglas conjunto) {
        String comercializadora = Optional.ofNullable(conjunto.getIdComercializadora())
                .flatMap(comercializadoraRepo::findById).map(TBComercializadoras::getNombre)
                .map(n -> primeraPalabra(sinAcentos(n))).filter(p -> !p.isEmpty())
                .orElse(conjunto.getIdComercializadora() == null ? "TABLA" : "C" + conjunto.getIdComercializadora());
        String fase = vacio(conjunto.getFasePrecio()) ? "FIJO" : sinAcentos(conjunto.getFasePrecio()).replaceAll("[^A-Z0-9]+", "");
        LocalDate fecha = conjunto.getVigenciaDesde() != null ? conjunto.getVigenciaDesde() : LocalDate.now();
        return unico(comercializadora + "-" + fase + "-" + fecha.format(FECHA_CODIGO),
                codigo -> conjuntoRepo.findByCodigo(codigo).isPresent());
    }

    /** Código de regla a partir del nombre, en mayúsculas y sin acentos («IRIS_PYME_LUZ_44_DEL_FEE»), único en la tabla. */
    public String codigoRegla(Integer idConjunto, String nombre) {
        String base = sinAcentos(nombre == null ? "" : nombre).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        Set<String> usados = new HashSet<>();
        reglaRepo.findByIdConjuntoReglas(idConjunto).forEach(r -> usados.add(r.getCodigo()));
        return unico(base.isEmpty() ? "REGLA" : base, usados::contains);
    }

    // ============================================================
    // A7 · Volver a borrador
    // ============================================================

    @Transactional
    public McasConjuntoReglas volverABorrador(Integer id) {
        McasConjuntoReglas conjunto = conjunto(id);
        if (!"VALIDADO".equals(conjunto.getEstado()))
            throw new IllegalStateException("Solo una tabla validada puede volver a borrador.");
        if (propuestaRepo.existsByIdConjuntoReglas(id))
            throw new IllegalStateException("La tabla «" + McasRuleSetValidator.nombreConjunto(conjunto)
                    + "» ya tiene comisiones calculadas y no puede volver a borrador; cree una nueva versión.");
        conjunto.setEstado("BORRADOR");
        return conjuntoRepo.save(conjunto);
    }

    // ============================================================
    // C4 · Nueva versión
    // ============================================================

    /**
     * Copia la tabla en BORRADOR con sus reglas (activas e inactivas) y sus
     * tramos, para que empiece en {@code vigenciaDesde}. Sin nombre, toma el de
     * la original con el mes nuevo («Endesa 2026-03» → «Endesa 2026-10»).
     */
    @Transactional
    public McasConjuntoReglas nuevaVersion(Integer id, LocalDate vigenciaDesde, String nombre) {
        McasConjuntoReglas origen = conjunto(id);
        if (vigenciaDesde == null)
            throw new IllegalArgumentException("Indique la fecha de inicio de la nueva versión.");
        if (origen.getVigenciaDesde() != null && !vigenciaDesde.isAfter(origen.getVigenciaDesde()))
            throw new IllegalArgumentException("La nueva versión debe empezar después del "
                    + origen.getVigenciaDesde().format(FECHA) + ", fecha de inicio de «"
                    + McasRuleSetValidator.nombreConjunto(origen) + "».");

        McasConjuntoReglas copia = McasConjuntoReglas.builder()
                .nombre(vacio(nombre) ? nombreNuevaVersion(origen, vigenciaDesde) : nombre.trim())
                .descripcion(origen.getDescripcion())
                .idConjuntoOrigen(origen.getId())
                .version(vigenciaDesde.format(FECHA_VERSION))
                .idComercializadora(origen.getIdComercializadora())
                .idGestora(origen.getIdGestora())
                .fasePrecio(origen.getFasePrecio())
                .vigenciaDesde(vigenciaDesde)
                .estado("BORRADOR")
                .archivoOrigen(origen.getArchivoOrigen())
                .hojaOrigen(origen.getHojaOrigen())
                .fechaInternaOrigen(origen.getFechaInternaOrigen())
                .hashOrigen(origen.getHashOrigen())
                .creadoPor(AuraUserHolder.get())
                .fechaCreacion(LocalDateTime.now())
                .build();
        copia.setCodigo(codigoConjunto(copia));
        copia = conjuntoRepo.save(copia);

        for (McasReglaComision regla : reglaRepo.findByIdConjuntoReglasOrderByPrioridadAsc(origen.getId())) {
            McasReglaComision nueva = reglaRepo.save(McasReglaComision.builder()
                    .idConjuntoReglas(copia.getId()).codigo(regla.getCodigo()).nombre(regla.getNombre())
                    .descripcion(regla.getDescripcion()).prioridad(regla.getPrioridad())
                    .metodoCalculo(regla.getMetodoCalculo()).condicionesJson(regla.getCondicionesJson())
                    .calculoJson(regla.getCalculoJson()).politicaPagoJson(regla.getPoliticaPagoJson())
                    .porcentajeResultado(regla.getPorcentajeResultado())
                    .motivoManual(regla.getMotivoManual()).localizadorOrigen(regla.getLocalizadorOrigen())
                    .activo(regla.getActivo()).build());
            List<McasValorRango> valores = new ArrayList<>();
            for (McasValorRango v : rangoRepo.findByIdReglaOrderByOrdenAsc(regla.getId()))
                valores.add(McasValorRango.builder().idRegla(nueva.getId()).dimension(v.getDimension())
                        .valorMin(v.getValorMin()).valorMax(v.getValorMax())
                        .minInclusivo(v.getMinInclusivo()).maxInclusivo(v.getMaxInclusivo())
                        .valorCategoria(v.getValorCategoria()).valorResultado(v.getValorResultado())
                        .unidadResultado(v.getUnidadResultado()).orden(v.getOrden()).build());
            rangoRepo.saveAll(valores);
        }
        return copia;
    }

    // ============================================================
    // C5 · Comprobar y activar
    // ============================================================

    /**
     * Qué pasaría al activar la tabla, sin cambiar nada: problemas que lo
     * impiden (por regla y celda), cambios que haría (vigencias que se cierran)
     * y avisos: propuestas pendientes calculadas con la versión anterior,
     * comisiones de la comercializadora que esperan una tabla y, si se cierra una
     * tabla «ambos», la otra fase que se queda sin tabla.
     */
    @Transactional(readOnly = true)
    public Comprobacion comprobacion(Integer id, boolean cerrarVigenciaAnterior) {
        McasConjuntoReglas conjunto = conjunto(id);
        List<McasIncidenciaDTO> incidencias = new ArrayList<>();
        String bloqueo = motivoEstado(conjunto);
        if (bloqueo != null) incidencias.add(McasIncidenciaDTO.builder().mensaje(bloqueo).build());
        String fase = motivoFaseBloqueada(conjunto);
        if (fase != null) incidencias.add(McasIncidenciaDTO.builder().mensaje(fase).build());
        Plan plan = planificar(conjunto, cerrarVigenciaAnterior);
        incidencias.addAll(plan.incidencias());

        List<String> cambios = new ArrayList<>();
        if (conjunto.getVigenciaDesde() != null)
            cambios.add("«" + McasRuleSetValidator.nombreConjunto(conjunto)
                    + "» se aplicará a los contratos que empiecen desde el " + conjunto.getVigenciaDesde().format(FECHA));
        plan.cierres().forEach(c -> cambios.add("Se cerrará «" + McasRuleSetValidator.nombreConjunto(c.conjunto())
                + "» el " + c.hasta().format(FECHA)));

        List<String> avisos = new ArrayList<>();
        for (Cierre cierre : plan.cierres()) {
            String otraFase = otraFase(conjunto.getFasePrecio());
            if ("AMBOS".equals(cierre.conjunto().getFasePrecio()) && otraFase != null)
                avisos.add("«" + McasRuleSetValidator.nombreConjunto(cierre.conjunto()) + "» también se aplicaba a los "
                        + ("INDEXADO".equals(otraFase) ? "contratos indexados" : "contratos a precio fijo") + ": desde el "
                        + conjunto.getVigenciaDesde().format(FECHA) + " no tendrán tabla activa hasta que active una.");
        }
        long pendientes = propuestasPendientes(plan.cierres().stream().map(c -> c.conjunto().getId()).toList(),
                conjunto.getVigenciaDesde());
        if (pendientes == 1)
            avisos.add("Hay 1 propuesta pendiente calculada con la versión anterior para contratos que empiezan desde el "
                    + conjunto.getVigenciaDesde().format(FECHA)
                    + "; no se recalcula sola: recalcúlela en Comisiones MCAS.");
        else if (pendientes > 1)
            avisos.add("Hay " + pendientes + " propuestas pendientes calculadas con la versión anterior para contratos "
                    + "que empiezan desde el " + conjunto.getVigenciaDesde().format(FECHA)
                    + "; no se recalculan solas: recalcúlelas en Comisiones MCAS.");
        long sinTabla = comisionesSinTabla(conjunto);
        if (sinTabla > 0) {
            String comercializadora = comercializadoraRepo.findById(conjunto.getIdComercializadora())
                    .map(TBComercializadoras::getNombre).map(String::trim).orElse("esta comercializadora");
            avisos.add(sinTabla == 1
                    ? "Hay 1 comisión de " + comercializadora
                            + " esperando una tabla: recalcúlela en Comisiones MCAS tras activar."
                    : "Hay " + sinTabla + " comisiones de " + comercializadora
                            + " esperando una tabla: recalcúlelas en Comisiones MCAS tras activar.");
        }
        return new Comprobacion(incidencias.isEmpty(), List.copyOf(incidencias), List.copyOf(cambios), List.copyOf(avisos));
    }

    /**
     * Valida, cierra la vigencia de la versión anterior (si se pide) y publica,
     * todo en una transacción: si algo falla no cambia nada.
     *
     * @throws IllegalStateException si la tabla ya está activa o archivada, o la fase está bloqueada
     * @throws McasRuleSetValidator.IncidenciasException si la configuración tiene problemas
     */
    @Transactional
    public McasConjuntoReglas activar(Integer id, boolean cerrarVigenciaAnterior) {
        return activarConCambios(id, cerrarVigenciaAnterior).conjunto();
    }

    /**
     * Activa cada tabla en su propia transacción y devuelve el resultado de cada
     * una (una que falla no deshace las demás).
     */
    public List<McasResultadoLoteDTO> activarLote(List<Integer> ids, boolean cerrarVigenciaAnterior) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("Indique qué tablas quiere activar.");
        List<Integer> unicos = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (unicos.size() > MAX_LOTE)
            throw new IllegalArgumentException("Puede activar como máximo " + MAX_LOTE + " tablas a la vez.");
        List<McasResultadoLoteDTO> resultados = new ArrayList<>();
        for (Integer id : unicos) {
            try {
                Activacion activacion = transaccion.execute(estado -> activarConCambios(id, cerrarVigenciaAnterior));
                McasConjuntoReglas c = Objects.requireNonNull(activacion).conjunto();
                StringBuilder mensaje = new StringBuilder("«" + McasRuleSetValidator.nombreConjunto(c)
                        + "» activada desde el " + c.getVigenciaDesde().format(FECHA) + ".");
                activacion.cierres().forEach(cierre -> mensaje.append(" Se ha cerrado «")
                        .append(McasRuleSetValidator.nombreConjunto(cierre.conjunto())).append("» el ")
                        .append(cierre.hasta().format(FECHA)).append("."));
                resultados.add(McasResultadoLoteDTO.ok(id.longValue(), c.getEstado(), mensaje.toString()));
            } catch (McasRuleSetValidator.IncidenciasException e) {
                List<String> detalles = e.getDetalles();
                String mensaje = e.getMessage() + (detalles.isEmpty() ? "" : " " + detalles.get(0)
                        + (detalles.size() > 1 ? " (y " + (detalles.size() - 1) + " más)" : ""));
                resultados.add(McasResultadoLoteDTO.error(id.longValue(), estadoActual(id), mensaje));
            } catch (NoSuchElementException | IllegalStateException | IllegalArgumentException e) {
                resultados.add(McasResultadoLoteDTO.error(id.longValue(), estadoActual(id), e.getMessage()));
            } catch (RuntimeException e) {
                log.error("MCAS: error inesperado al activar la tabla {}", id, e);
                resultados.add(McasResultadoLoteDTO.error(id.longValue(), estadoActual(id),
                        "No se ha podido activar por un error inesperado; inténtelo de nuevo."));
            }
        }
        return resultados;
    }

    private Activacion activarConCambios(Integer id, boolean cerrarVigenciaAnterior) {
        McasConjuntoReglas conjunto = conjunto(id);
        String bloqueo = motivoEstado(conjunto);
        if (bloqueo != null) throw new IllegalStateException(bloqueo);
        String fase = motivoFaseBloqueada(conjunto);
        if (fase != null) throw new IllegalStateException(fase);
        Plan plan = planificar(conjunto, cerrarVigenciaAnterior);
        if (!plan.incidencias().isEmpty()) {
            int n = plan.incidencias().size();
            throw new McasRuleSetValidator.IncidenciasException("No se puede activar «"
                    + McasRuleSetValidator.nombreConjunto(conjunto) + "»: hay "
                    + (n == 1 ? "1 problema" : n + " problemas") + " por resolver.", plan.incidencias());
        }
        for (Cierre cierre : plan.cierres()) {
            cierre.conjunto().setVigenciaHasta(cierre.hasta());
            conjuntoRepo.save(cierre.conjunto());
        }
        conjunto.setEstado("PUBLICADO");
        conjunto.setAprobadoPor(AuraUserHolder.get());
        conjunto.setFechaAprobacion(LocalDateTime.now());
        return new Activacion(conjuntoRepo.save(conjunto), plan.cierres());
    }

    private record Cierre(McasConjuntoReglas conjunto, LocalDate hasta) {}

    private record Plan(List<McasIncidenciaDTO> incidencias, List<Cierre> cierres) {}

    private record Activacion(McasConjuntoReglas conjunto, List<Cierre> cierres) {}

    /**
     * Tablas activas que hay que cerrar (misma comercializadora y gestora, fase
     * solapada como en el validador —igual, o una de las dos «ambos»—, empiezan
     * antes y siguen vigentes: terminan el día anterior a la nueva) y problemas de
     * la validación de publicación con esos cierres ya aplicados. Una tabla activa
     * que empieza el mismo día o después no se cierra: es un problema.
     */
    private Plan planificar(McasConjuntoReglas conjunto, boolean cerrarVigenciaAnterior) {
        List<McasIncidenciaDTO> incidencias = new ArrayList<>();
        List<Cierre> cierres = new ArrayList<>();
        Set<String> conflictos = new HashSet<>();
        if (cerrarVigenciaAnterior && conjunto.getIdComercializadora() != null && conjunto.getVigenciaDesde() != null) {
            LocalDate desde = conjunto.getVigenciaDesde();
            for (McasConjuntoReglas activa : conjuntoRepo.findByIdComercializadoraAndEstado(
                    conjunto.getIdComercializadora(), "PUBLICADO")) {
                if (Objects.equals(activa.getId(), conjunto.getId())
                        || !Objects.equals(activa.getIdGestora(), conjunto.getIdGestora())
                        || !fasesSolapan(activa.getFasePrecio(), conjunto.getFasePrecio())
                        || activa.getVigenciaDesde() == null
                        || !solapan(activa, conjunto)) continue;
                String nombre = McasRuleSetValidator.nombreConjunto(activa);
                if (activa.getVigenciaDesde().isBefore(desde)) {
                    cierres.add(new Cierre(activa, desde.minusDays(1)));
                } else {
                    conflictos.add("«" + nombre + "»");
                    incidencias.add(McasIncidenciaDTO.builder().mensaje("La tabla activa «" + nombre + "» empieza el "
                            + activa.getVigenciaDesde().format(FECHA) + ", el mismo día o después que esta, "
                            + "y no se puede cerrar: cambie la fecha de inicio de esta tabla o archive «" + nombre + "».")
                            .build());
                }
            }
        }
        Map<Integer, LocalDate> previstos = new HashMap<>();
        cierres.forEach(c -> previstos.put(c.conjunto().getId(), c.hasta()));
        Predicate<McasIncidenciaDTO> repetida = i -> i.getIdRegla() == null && i.getMensaje() != null
                && i.getMensaje().startsWith("Coincide en fechas con la tabla activa")
                && conflictos.stream().anyMatch(i.getMensaje()::contains);
        validator.validarPublicacion(conjunto, previstos).incidencias().stream()
                .filter(repetida.negate()).forEach(incidencias::add);
        return new Plan(incidencias, cierres);
    }

    /** Mismo criterio que {@link McasRuleSetValidator#validarPublicacion}: igual fase, o una de las dos «ambos». */
    private static boolean fasesSolapan(String a, String b) {
        return Objects.equals(a, b) || "AMBOS".equals(a) || "AMBOS".equals(b);
    }

    private static boolean solapan(McasConjuntoReglas a, McasConjuntoReglas b) {
        LocalDate finA = a.getVigenciaHasta() != null ? a.getVigenciaHasta() : LocalDate.MAX;
        LocalDate finB = b.getVigenciaHasta() != null ? b.getVigenciaHasta() : LocalDate.MAX;
        return !finA.isBefore(b.getVigenciaDesde()) && !finB.isBefore(a.getVigenciaDesde());
    }

    /** Fase que una tabla «ambos» cubría y la nueva no: INDEXADO para una tabla FIJO y al revés. */
    private static String otraFase(String fase) {
        if ("FIJO".equals(fase)) return "INDEXADO";
        if ("INDEXADO".equals(fase)) return "FIJO";
        return null;
    }

    /**
     * Propuestas que se quedaron sin tabla (idConjuntoReglas nulo: «sin tabla activa»,
     * faltan datos…) de contratos de la comercializadora que empiezan dentro de la
     * vigencia de la tabla. Se excluyen los de la otra fase (indexado para una tabla
     * fija y al revés) y, si la tabla es de una gestora, los de otras gestoras.
     */
    private long comisionesSinTabla(McasConjuntoReglas conjunto) {
        if (conjunto.getIdComercializadora() == null || conjunto.getVigenciaDesde() == null) return 0;
        StringBuilder jpql = new StringBuilder("SELECT COUNT(p) FROM McasPropuesta p, Contrato c"
                + " WHERE c.id = p.idContrato AND p.idConjuntoReglas IS NULL AND p.estado IN :estados"
                + " AND c.idComercializadoraActual = :comercializadora AND c.fechaInicio >= :desde");
        if (conjunto.getVigenciaHasta() != null) jpql.append(" AND c.fechaInicio <= :hasta");
        String otraFase = otraFase(conjunto.getFasePrecio());
        if (otraFase != null) jpql.append(" AND (c.mcasTipoPrecio IS NULL OR UPPER(c.mcasTipoPrecio) <> :otraFase)");
        if (conjunto.getIdGestora() != null) jpql.append(" AND c.mcasIdGestora = :gestora");
        var consulta = em.createQuery(jpql.toString(), Long.class)
                .setParameter("estados", PROPUESTAS_SIN_TABLA)
                .setParameter("comercializadora", conjunto.getIdComercializadora())
                .setParameter("desde", java.sql.Date.valueOf(conjunto.getVigenciaDesde()));
        if (conjunto.getVigenciaHasta() != null)
            consulta.setParameter("hasta", java.sql.Date.valueOf(conjunto.getVigenciaHasta()));
        if (otraFase != null) consulta.setParameter("otraFase", otraFase);
        if (conjunto.getIdGestora() != null) consulta.setParameter("gestora", conjunto.getIdGestora());
        return consulta.getSingleResult();
    }

    /** Propuestas pendientes de las tablas que se cierran, de contratos que ya empiezan con la nueva. */
    private long propuestasPendientes(List<Integer> idsConjunto, LocalDate desde) {
        if (idsConjunto.isEmpty() || desde == null) return 0;
        return em.createQuery("SELECT COUNT(p) FROM McasPropuesta p, Contrato c WHERE c.id = p.idContrato"
                        + " AND p.idConjuntoReglas IN :ids AND p.estado IN :estados AND c.fechaInicio >= :desde", Long.class)
                .setParameter("ids", idsConjunto)
                .setParameter("estados", PROPUESTAS_PENDIENTES)
                .setParameter("desde", java.sql.Date.valueOf(desde))
                .getSingleResult();
    }

    // ============================================================
    // Auxiliares
    // ============================================================

    private McasConjuntoReglas conjunto(Integer id) {
        return conjuntoRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe la tabla de comisiones " + id + "."));
    }

    private String estadoActual(Integer id) {
        return conjuntoRepo.findById(id).map(McasConjuntoReglas::getEstado).orElse(null);
    }

    /** Motivo por el que el estado impide activar; null si está en borrador o validada. */
    private static String motivoEstado(McasConjuntoReglas conjunto) {
        return switch (String.valueOf(conjunto.getEstado())) {
            case "BORRADOR", "VALIDADO" -> null;
            case "PUBLICADO" -> "La tabla «" + McasRuleSetValidator.nombreConjunto(conjunto) + "» ya está activa.";
            case "ARCHIVADO" -> "La tabla «" + McasRuleSetValidator.nombreConjunto(conjunto)
                    + "» está archivada: cree una nueva versión para volver a usarla.";
            default -> "La tabla «" + McasRuleSetValidator.nombreConjunto(conjunto)
                    + "» no se puede activar en su estado actual.";
        };
    }

    /** «Endesa 2026-03» → «Endesa 2026-10»; sin mes al final, se añade. */
    private static String nombreNuevaVersion(McasConjuntoReglas origen, LocalDate desde) {
        String base = McasRuleSetValidator.nombreConjunto(origen).replaceFirst("\\s*\\d{4}-\\d{2}$", "");
        String nombre = base + " " + desde.format(MES);
        return nombre.length() > 150 ? nombre.substring(0, 150) : nombre;
    }

    private static String unico(String base, Predicate<String> existe) {
        String recortada = base.length() > MAX_CODIGO ? base.substring(0, MAX_CODIGO) : base;
        if (!existe.test(recortada)) return recortada;
        for (int n = 2; ; n++) {
            String sufijo = "-" + n;
            String candidato = (base.length() + sufijo.length() > MAX_CODIGO
                    ? base.substring(0, MAX_CODIGO - sufijo.length()) : base) + sufijo;
            if (!existe.test(candidato)) return candidato;
        }
    }

    private static String sinAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
    }

    private static String primeraPalabra(String texto) {
        return Arrays.stream(texto.split("[^A-Z0-9]+")).filter(p -> !p.isEmpty()).findFirst().orElse("");
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String limpio(String texto) {
        return vacio(texto) ? null : texto.trim();
    }
}
