package api.aura.service.mcas;

import api.aura.dto.mcas.McasPoliticaRetrocesionDTO;
import api.aura.dto.mcas.McasTramoRetrocesionDTO;
import api.aura.entity.mcas.McasPoliticaRetrocesion;
import api.aura.entity.mcas.McasTramoRetrocesion;
import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.entity.tablas_maestras.TBGestoras;
import api.aura.repository.mcas.McasPoliticaRetrocesionRepository;
import api.aura.repository.mcas.McasTramoRetrocesionRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.repository.tablas_maestras.TBGestorasRepository;
import api.aura.service.mcas.engine.ContextoCalculo;
import api.aura.service.mcas.engine.McasTexto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Políticas de retrocesión: gestión desde Configuración MCAS y asociación
 * informativa a las propuestas («retrocesión potencial»). No calcula ni descuenta
 * retrocesiones: aplicarlas en las bajas queda para una versión futura.
 */
@Service
public class McasRetrocesionPolicyService {

    public static final List<String> UNIDADES = List.of("DIAS", "MESES");
    public static final List<String> TIPOS_VALOR = List.of("PORCENTAJE", "IMPORTE_FIJO", "FACTOR", "SIN_CONFIRMAR");
    /** Métodos que no se expresan con tramos. */
    private static final List<String> METODOS_SIN_TRAMOS = List.of("PRORRATEO", "NO_APLICA");

    private final McasPoliticaRetrocesionRepository repo;
    private final McasTramoRetrocesionRepository tramoRepo;
    private final TBComercializadorasRepository comercializadoraRepo;
    private final TBGestorasRepository gestoraRepo;

    public McasRetrocesionPolicyService(McasPoliticaRetrocesionRepository repo, McasTramoRetrocesionRepository tramoRepo,
                                        TBComercializadorasRepository comercializadoraRepo, TBGestorasRepository gestoraRepo) {
        this.repo = repo;
        this.tramoRepo = tramoRepo;
        this.comercializadoraRepo = comercializadoraRepo;
        this.gestoraRepo = gestoraRepo;
    }

    // ============================================================
    // Asociación a la propuesta (informativa)
    // ============================================================

    public Optional<Integer> seleccionar(ContextoCalculo ctx) {
        if (ctx.getIdComercializadora() == null) return Optional.empty();
        LocalDate fecha = ctx.getFechaEfectiva() != null ? ctx.getFechaEfectiva() : LocalDate.now();
        List<McasPoliticaRetrocesion> candidatas = repo
                .findPublicadasVigentes(ctx.getIdComercializadora(), fecha).stream()
                .filter(p -> coincide(p.getIdGestora(), ctx.getIdGestora()))
                .filter(p -> coincide(p.getTipoSuministro(), ctx.getTipoSuministro()))
                .filter(p -> coincide(p.getSegmento(), ctx.getSegmento()))
                .filter(p -> p.getTarifaAcceso() == null || McasTexto.mismaTarifa(p.getTarifaAcceso(), ctx.getCodigoTarifaAcceso()))
                .sorted(Comparator.comparingInt(this::especificidad).reversed())
                .toList();
        if (candidatas.isEmpty()) return Optional.empty();
        int maxima = especificidad(candidatas.get(0));
        List<McasPoliticaRetrocesion> mejores = candidatas.stream()
                .filter(p -> especificidad(p) == maxima).toList();
        if (mejores.size() > 1)
            throw new IllegalStateException(mejores.size()
                    + " politicas de retrocesion PUBLICADAS coinciden con igual especificidad");
        return Optional.of(mejores.get(0).getId());
    }

    // ============================================================
    // Resumen en lenguaje natural
    // ============================================================

    /** «PYME — desde la activación: 0–3 meses: 100 %; 3–6 meses: 60 %. Nunca supera lo cobrado.» */
    public Optional<String> describir(Integer idPolitica) {
        if (idPolitica == null) return Optional.empty();
        return repo.findById(idPolitica).map(p -> resumen(p,
                tramoRepo.findByIdPoliticaOrderByUnidadAscDesdeValorAscPrioridadAsc(idPolitica)));
    }

    /** Resúmenes de varias políticas con dos consultas en total (listado de propuestas). */
    public Map<Integer, String> describirTodas(Collection<Integer> ids) {
        Set<Integer> unicos = ids == null ? Set.of()
                : ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (unicos.isEmpty()) return Map.of();
        Map<Integer, List<McasTramoRetrocesion>> tramos = tramosDe(unicos);
        Map<Integer, String> resumenes = new HashMap<>();
        for (McasPoliticaRetrocesion p : repo.findAllById(unicos))
            resumenes.put(p.getId(), resumen(p, tramos.getOrDefault(p.getId(), List.of())));
        return resumenes;
    }

    private String resumen(McasPoliticaRetrocesion p, List<McasTramoRetrocesion> tramos) {
        String limite = Boolean.TRUE.equals(p.getLimiteImportePagado()) ? " Nunca supera lo cobrado." : "";
        if ("NO_APLICA".equals(p.getMetodo())) return "No aplica retrocesión";
        if ("PRORRATEO".equals(p.getMetodo())) return "Prorrateo a 365 días desde la activación." + limite;
        String ambito = Stream.of(p.getSegmento(), p.getTipoSuministro(), p.getTarifaAcceso())
                .filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(" · "));
        String detalle = tramos.isEmpty() ? "sin tramos definidos"
                : tramos.stream().sorted(ORDEN_TRAMOS)
                .map(t -> rango(t) + ": " + valor(t)).collect(Collectors.joining("; "));
        return (ambito.isEmpty() ? "Desde la activación: " : ambito + " — desde la activación: ")
                + detalle + "." + limite;
    }

    private static final Comparator<McasTramoRetrocesion> ORDEN_TRAMOS = Comparator
            .comparing(McasTramoRetrocesion::getUnidad, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(McasTramoRetrocesion::getDesdeValor, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(McasTramoRetrocesion::getPrioridad, Comparator.nullsLast(Comparator.naturalOrder()));

    private String rango(McasTramoRetrocesion t) {
        String unidad = "DIAS".equals(t.getUnidad()) ? "días" : "meses";
        if (t.getHastaValor() == null)
            return (Boolean.FALSE.equals(t.getDesdeInclusivo()) ? "más de " : "desde ") + t.getDesdeValor() + " " + unidad;
        return t.getDesdeValor() + "–" + t.getHastaValor() + " " + unidad;
    }

    private String valor(McasTramoRetrocesion t) {
        if (t.getValor() == null) return "sin valor";
        return switch (t.getTipoValor() == null ? "" : t.getTipoValor()) {
            case "PORCENTAJE" -> numero(t.getValor().movePointRight(2)) + " %";
            case "FACTOR" -> "factor " + numero(t.getValor());
            case "IMPORTE_FIJO" -> numero(t.getValor()) + " €";
            default -> numero(t.getValor()) + " (tipo de valor sin elegir)";
        };
    }

    private static String numero(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0.######",
                DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-ES")));
        return formato.format(valor);
    }

    // ============================================================
    // Gestión (Configuración MCAS)
    // ============================================================

    @Transactional(readOnly = true)
    public List<McasPoliticaRetrocesionDTO> listar() {
        List<McasPoliticaRetrocesion> todas = repo.findAll().stream()
                .sorted(Comparator.comparing(McasPoliticaRetrocesion::getIdComercializadora,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(McasPoliticaRetrocesion::getVigenciaDesde,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(McasPoliticaRetrocesion::getId))
                .toList();
        return aDtos(todas);
    }

    @Transactional(readOnly = true)
    public McasPoliticaRetrocesionDTO obtener(Integer id) {
        return aDtos(List.of(cargar(id))).get(0);
    }

    /** Nace siempre en BORRADOR: publicar es un paso aparte, con su validación. */
    @Transactional
    public McasPoliticaRetrocesionDTO crear(McasPoliticaRetrocesionDTO datos) {
        McasPoliticaRetrocesion p = new McasPoliticaRetrocesion();
        copiar(datos, p, null);
        p.setEstado("BORRADOR");
        List<McasTramoRetrocesion> tramos = tramosDesde(datos);
        p = repo.save(p);
        guardarTramos(p.getId(), tramos);
        return obtener(p.getId());
    }

    /** Solo en BORRADOR; los tramos se sustituyen por los recibidos. */
    @Transactional
    public McasPoliticaRetrocesionDTO actualizar(Integer id, McasPoliticaRetrocesionDTO datos) {
        McasPoliticaRetrocesion p = cargar(id);
        exigirBorrador(p, "editar");
        copiar(datos, p, id);
        List<McasTramoRetrocesion> tramos = tramosDesde(datos);
        repo.save(p);
        tramoRepo.deleteByIdPolitica(id);
        guardarTramos(id, tramos);
        return obtener(id);
    }

    @Transactional
    public void eliminar(Integer id) {
        McasPoliticaRetrocesion p = cargar(id);
        exigirBorrador(p, "eliminar");
        long propuestas = repo.contarPropuestas(id);
        if (propuestas > 0)
            throw new IllegalStateException("La política está asociada a " + propuestas
                    + " propuestas: archívela en lugar de eliminarla");
        tramoRepo.deleteByIdPolitica(id);
        repo.deleteById(id);
    }

    /**
     * Valida y publica. Una política con algún tramo sin tipo de valor elegido
     * (SIN_CONFIRMAR) no se publica hasta editarla: mostraría una retrocesión inventada.
     */
    @Transactional
    public McasPoliticaRetrocesionDTO publicar(Integer id) {
        McasPoliticaRetrocesion p = cargar(id);
        exigirBorrador(p, "publicar");
        List<McasTramoRetrocesion> tramos = tramoRepo.findByIdPoliticaOrderByUnidadAscDesdeValorAscPrioridadAsc(id);
        validarPublicacion(p, tramos);
        p.setEstado("PUBLICADO");
        repo.save(p);
        return obtener(id);
    }

    @Transactional
    public McasPoliticaRetrocesionDTO archivar(Integer id) {
        McasPoliticaRetrocesion p = cargar(id);
        if (!"PUBLICADO".equals(p.getEstado()) && !"BORRADOR".equals(p.getEstado()))
            throw new IllegalStateException("Solo se puede archivar una política publicada o en borrador (esta está "
                    + estadoLegible(p.getEstado()) + ").");
        p.setEstado("ARCHIVADO");
        repo.save(p);
        return obtener(id);
    }

    // ============================================================
    // Validación
    // ============================================================

    private void validarPublicacion(McasPoliticaRetrocesion p, List<McasTramoRetrocesion> tramos) {
        boolean sinTramos = METODOS_SIN_TRAMOS.contains(p.getMetodo());
        if (!sinTramos && tramos.isEmpty())
            throw new IllegalArgumentException("La política necesita al menos un tramo para publicarse");
        for (McasTramoRetrocesion t : tramos) {
            validarEstructura(t);
            if ("SIN_CONFIRMAR".equals(t.getTipoValor()))
                throw new IllegalArgumentException("El tramo " + rango(t) + " no tiene elegido el tipo de valor: "
                        + "elija porcentaje, factor o importe fijo y guarde la política antes de publicarla.");
            if (t.getValor().signum() < 0)
                throw new IllegalArgumentException("El tramo " + rango(t) + " tiene un valor negativo");
            if (List.of("PORCENTAJE", "FACTOR").contains(t.getTipoValor()) && t.getValor().compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("El tramo " + rango(t)
                        + ": el porcentaje se indica como factor entre 0 y 1 (0,6 = 60 %)");
        }
        for (int i = 0; i < tramos.size(); i++)
            for (int j = i + 1; j < tramos.size(); j++) {
                McasTramoRetrocesion a = tramos.get(i), b = tramos.get(j);
                if (a.getUnidad().equals(b.getUnidad()) && seSolapan(a, b))
                    throw new IllegalArgumentException("Los tramos " + rango(a) + " y " + rango(b) + " se solapan");
            }
        List<McasPoliticaRetrocesion> conflicto = repo.findByIdComercializadoraAndEstado(p.getIdComercializadora(), "PUBLICADO")
                .stream().filter(o -> !o.getId().equals(p.getId()) && mismoAmbito(o, p) && vigenciasSeSolapan(o, p))
                .toList();
        if (!conflicto.isEmpty())
            throw new IllegalStateException("Ya hay una política publicada para la misma comercializadora, gestora, "
                    + "suministro, segmento y tarifa con vigencia solapada ("
                    + conflicto.stream().map(this::nombre).collect(Collectors.joining(", "))
                    + "): archívela o cierre su vigencia antes de publicar esta");
    }

    /** Datos mínimos de un tramo (también al guardar un borrador: las columnas son NOT NULL). */
    private void validarEstructura(McasTramoRetrocesion t) {
        if (t.getUnidad() == null || !UNIDADES.contains(t.getUnidad()))
            throw new IllegalArgumentException("La unidad del tramo debe ser días o meses.");
        if (t.getDesdeValor() == null || t.getDesdeValor() < 0)
            throw new IllegalArgumentException("Cada tramo necesita un «desde» mayor o igual que 0");
        if (t.getHastaValor() != null && t.getHastaValor() <= t.getDesdeValor())
            throw new IllegalArgumentException("El «hasta» del tramo " + t.getDesdeValor() + "–" + t.getHastaValor()
                    + " debe ser mayor que el «desde»");
        if (t.getTipoValor() == null || !TIPOS_VALOR.contains(t.getTipoValor()))
            throw new IllegalArgumentException("Elija el tipo de valor del tramo: porcentaje, factor o importe fijo.");
        if (t.getValor() == null)
            throw new IllegalArgumentException("Cada tramo necesita un valor");
    }

    /** Dos tramos de la misma unidad comparten algún instante, respetando qué extremos son inclusivos. */
    private boolean seSolapan(McasTramoRetrocesion a, McasTramoRetrocesion b) {
        return empiezaAntesDelFin(a, b) && empiezaAntesDelFin(b, a);
    }

    private boolean empiezaAntesDelFin(McasTramoRetrocesion x, McasTramoRetrocesion y) {
        if (y.getHastaValor() == null) return true;
        int cmp = Integer.compare(x.getDesdeValor(), y.getHastaValor());
        if (cmp != 0) return cmp < 0;
        return !Boolean.FALSE.equals(x.getDesdeInclusivo()) && Boolean.TRUE.equals(y.getHastaInclusivo());
    }

    private boolean mismoAmbito(McasPoliticaRetrocesion a, McasPoliticaRetrocesion b) {
        return Objects.equals(a.getIdGestora(), b.getIdGestora())
                && igualTexto(a.getTipoSuministro(), b.getTipoSuministro())
                && igualTexto(a.getSegmento(), b.getSegmento())
                && Objects.equals(McasTexto.normalizarTarifa(a.getTarifaAcceso()), McasTexto.normalizarTarifa(b.getTarifaAcceso()));
    }

    private boolean vigenciasSeSolapan(McasPoliticaRetrocesion a, McasPoliticaRetrocesion b) {
        LocalDate finA = a.getVigenciaHasta() != null ? a.getVigenciaHasta() : LocalDate.MAX;
        LocalDate finB = b.getVigenciaHasta() != null ? b.getVigenciaHasta() : LocalDate.MAX;
        return !a.getVigenciaDesde().isAfter(finB) && !b.getVigenciaDesde().isAfter(finA);
    }

    private void exigirBorrador(McasPoliticaRetrocesion p, String accion) {
        if (!"BORRADOR".equals(p.getEstado()))
            throw new IllegalStateException("Solo se puede " + accion + " una política en borrador (esta está "
                    + estadoLegible(p.getEstado()) + ").");
    }

    private static String estadoLegible(String estado) {
        return switch (String.valueOf(estado)) {
            case "PUBLICADO" -> "publicada";
            case "ARCHIVADO" -> "archivada";
            case "VALIDADO" -> "validada";
            default -> "en estado " + McasMensajes.estadoPropuesta(estado).toLowerCase(Locale.ROOT);
        };
    }

    // ============================================================
    // Conversión
    // ============================================================

    /** Copia los datos editables y valida los obligatorios. */
    private void copiar(McasPoliticaRetrocesionDTO d, McasPoliticaRetrocesion p, Integer idActual) {
        if (d == null) throw new IllegalArgumentException("Faltan los datos de la política");
        if (d.getIdComercializadora() == null || d.getIdComercializadora() == 0)
            throw new IllegalArgumentException("Indique la comercializadora");
        if (d.getVigenciaDesde() == null)
            throw new IllegalArgumentException("Indique el inicio de la vigencia");
        if (d.getVigenciaHasta() != null && d.getVigenciaHasta().isBefore(d.getVigenciaDesde()))
            throw new IllegalArgumentException("El fin de la vigencia no puede ser anterior a su inicio");
        if (vacio(d.getMetodo()))
            throw new IllegalArgumentException("Indique el método de retrocesión");
        // Sin código: al crear se genera; al editar se conserva el que tenía
        String codigo = !vacio(d.getCodigo()) ? d.getCodigo().trim()
                : idActual != null && !vacio(p.getCodigo()) ? p.getCodigo() : codigoPolitica(d);
        repo.findByCodigo(codigo).filter(o -> !o.getId().equals(idActual)).ifPresent(o -> {
            throw new IllegalArgumentException("Ya existe otra política con el código " + codigo + ".");
        });
        p.setCodigo(codigo);
        p.setIdComercializadora(d.getIdComercializadora());
        p.setIdGestora(d.getIdGestora() != null && d.getIdGestora() != 0 ? d.getIdGestora() : null);
        p.setVersion(vacio(d.getVersion()) ? "1" : d.getVersion().trim());
        p.setVigenciaDesde(d.getVigenciaDesde());
        p.setVigenciaHasta(d.getVigenciaHasta());
        p.setTipoSuministro(mayusculas(d.getTipoSuministro()));
        p.setSegmento(mayusculas(d.getSegmento()));
        p.setTarifaAcceso(vacio(d.getTarifaAcceso()) ? null : d.getTarifaAcceso().trim());
        p.setMetodo(d.getMetodo().trim().toUpperCase(Locale.ROOT));
        p.setLimiteImportePagado(d.getLimiteImportePagado() == null || d.getLimiteImportePagado());
        p.setDescripcion(vacio(d.getDescripcion()) ? null : d.getDescripcion().trim());
    }

    /**
     * Código de una política nueva sin código: «RET-», la comercializadora (primera
     * palabra), el segmento y el suministro si los tiene y la fecha de inicio
     * («RET-IBERDROLA-PYME-LUZ-20260101»), con «-2», «-3»… si ya existe.
     */
    private String codigoPolitica(McasPoliticaRetrocesionDTO d) {
        String comercializadora = comercializadoraRepo.findById(d.getIdComercializadora())
                .map(TBComercializadoras::getNombre).map(McasTexto::primeraPalabra).filter(n -> !n.isEmpty())
                .orElse("C" + d.getIdComercializadora());
        String base = String.join("-", Stream.of("RET", comercializadora, McasTexto.codigo(d.getSegmento()),
                        McasTexto.codigo(d.getTipoSuministro()), d.getVigenciaDesde().format(FECHA_CODIGO))
                .filter(s -> !s.isEmpty()).toList());
        if (base.length() > 95) base = base.substring(0, 95);
        String codigo = base;
        for (int n = 2; repo.findByCodigo(codigo).isPresent(); n++) codigo = base + "-" + n;
        return codigo;
    }

    private static final java.time.format.DateTimeFormatter FECHA_CODIGO =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");

    private List<McasTramoRetrocesion> tramosDesde(McasPoliticaRetrocesionDTO d) {
        List<McasTramoRetrocesion> tramos = new ArrayList<>();
        if (d.getTramos() == null) return tramos;
        for (McasTramoRetrocesionDTO t : d.getTramos()) {
            McasTramoRetrocesion tramo = McasTramoRetrocesion.builder()
                    .unidad(mayusculas(t.getUnidad())).desdeValor(t.getDesdeValor()).hastaValor(t.getHastaValor())
                    .desdeInclusivo(t.getDesdeInclusivo() == null || t.getDesdeInclusivo())
                    .hastaInclusivo(t.getHastaInclusivo() != null && t.getHastaInclusivo())
                    .valor(t.getValor()).tipoValor(mayusculas(t.getTipoValor()))
                    .prioridad(t.getPrioridad() != null ? t.getPrioridad() : 100).build();
            validarEstructura(tramo);
            tramos.add(tramo);
        }
        return tramos;
    }

    private void guardarTramos(Integer idPolitica, List<McasTramoRetrocesion> tramos) {
        if (tramos.isEmpty()) return;
        tramos.forEach(t -> t.setIdPolitica(idPolitica));
        tramoRepo.saveAll(tramos);
    }

    /** DTOs con nombres y tramos: una consulta por catálogo para toda la lista. */
    private List<McasPoliticaRetrocesionDTO> aDtos(List<McasPoliticaRetrocesion> politicas) {
        if (politicas.isEmpty()) return List.of();
        Map<Integer, List<McasTramoRetrocesion>> tramos = tramosDe(
                politicas.stream().map(McasPoliticaRetrocesion::getId).toList());
        Map<Integer, String> comercializadoras = comercializadoraRepo.findAllById(
                        politicas.stream().map(McasPoliticaRetrocesion::getIdComercializadora)
                                .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .filter(c -> c.getNombre() != null)
                .collect(Collectors.toMap(TBComercializadoras::getId, TBComercializadoras::getNombre, (a, b) -> a));
        Map<Integer, String> gestoras = gestoraRepo.findAllById(
                        politicas.stream().map(McasPoliticaRetrocesion::getIdGestora)
                                .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .filter(g -> g.getNombre() != null)
                .collect(Collectors.toMap(TBGestoras::getId, TBGestoras::getNombre, (a, b) -> a));
        return politicas.stream().map(p -> {
            List<McasTramoRetrocesion> propios = tramos.getOrDefault(p.getId(), List.of());
            McasPoliticaRetrocesionDTO d = new McasPoliticaRetrocesionDTO();
            d.setId(p.getId()); d.setCodigo(p.getCodigo());
            d.setIdComercializadora(p.getIdComercializadora());
            d.setNombreComercializadora(comercializadoras.get(p.getIdComercializadora()));
            d.setIdGestora(p.getIdGestora()); d.setNombreGestora(gestoras.get(p.getIdGestora()));
            d.setVersion(p.getVersion()); d.setVigenciaDesde(p.getVigenciaDesde()); d.setVigenciaHasta(p.getVigenciaHasta());
            d.setTipoSuministro(p.getTipoSuministro()); d.setSegmento(p.getSegmento()); d.setTarifaAcceso(p.getTarifaAcceso());
            d.setMetodo(p.getMetodo()); d.setLimiteImportePagado(p.getLimiteImportePagado());
            d.setEstado(p.getEstado()); d.setDescripcion(p.getDescripcion());
            d.setResumen(resumen(p, propios));
            d.setTramos(propios.stream().sorted(ORDEN_TRAMOS).map(this::aDto).collect(Collectors.toList()));
            return d;
        }).collect(Collectors.toList());
    }

    private McasTramoRetrocesionDTO aDto(McasTramoRetrocesion t) {
        McasTramoRetrocesionDTO d = new McasTramoRetrocesionDTO();
        d.setId(t.getId()); d.setUnidad(t.getUnidad());
        d.setDesdeValor(t.getDesdeValor()); d.setHastaValor(t.getHastaValor());
        d.setDesdeInclusivo(t.getDesdeInclusivo()); d.setHastaInclusivo(t.getHastaInclusivo());
        d.setValor(t.getValor()); d.setTipoValor(t.getTipoValor()); d.setPrioridad(t.getPrioridad());
        return d;
    }

    private Map<Integer, List<McasTramoRetrocesion>> tramosDe(Collection<Integer> idsPolitica) {
        return tramoRepo.findByIdPoliticaInOrderByUnidadAscDesdeValorAscPrioridadAsc(idsPolitica).stream()
                .collect(Collectors.groupingBy(McasTramoRetrocesion::getIdPolitica, Collectors.toList()));
    }

    private McasPoliticaRetrocesion cargar(Integer id) {
        return repo.findById(id).orElseThrow(() ->
                new NoSuchElementException("Política de retrocesión no encontrada: " + id));
    }

    private String nombre(McasPoliticaRetrocesion p) {
        return p.getCodigo() != null ? p.getCodigo() : "#" + p.getId();
    }

    private boolean coincide(Object configurado, Object actual) {
        if (configurado == null) return true;
        return configurado instanceof String s && actual instanceof String a
                ? s.equalsIgnoreCase(a) : configurado.equals(actual);
    }

    private boolean igualTexto(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static String mayusculas(String s) {
        return vacio(s) ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private int especificidad(McasPoliticaRetrocesion p) {
        int score = 0;
        if (p.getIdGestora() != null) score += 8;
        if (p.getTipoSuministro() != null) score += 4;
        if (p.getSegmento() != null) score += 2;
        if (p.getTarifaAcceso() != null) score += 1;
        return score;
    }
}
