package api.aura.service.mcas;

import api.aura.config.AuraUserHolder;
import api.aura.entity.Contrato;
import api.aura.entity.PuntoSuministro;
import api.aura.entity.Servicio;
import api.aura.entity.ServiciosPagos;
import api.aura.entity.mcas.McasPoliticaRetrocesion;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.entity.mcas.McasRetrocesion;
import api.aura.entity.mcas.McasTramoRetrocesion;
import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.repository.ContratoRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.repository.ServicioRepository;
import api.aura.repository.ServiciosPagosRepository;
import api.aura.repository.mcas.McasPoliticaRetrocesionRepository;
import api.aura.repository.mcas.McasPropuestaRepository;
import api.aura.repository.mcas.McasRetrocesionRepository;
import api.aura.repository.mcas.McasTramoRetrocesionRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;
import api.aura.service.ModuloConfiguracionService;
import api.aura.util.BajaEfectiva;
import api.aura.util.FechaNegocio;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Retrocesiones automáticas (respuesta del cliente a la duda 20). La genera la baja del
 * contrato, la del punto (que arrastra al contrato) o el cambio de comercializadora, siempre
 * antes de los 365 días desde la fecha de inicio. El importe es el del tramo de la política
 * sobre lo cobrado de la comisión, y las cuotas sin cobrar posteriores a la baja se anulan.
 * Nada se mueve hasta que un administrador la aprueba en Comisiones MCAS.
 */
@Service
@RequiredArgsConstructor
public class McasRetrocesionService {

    private static final Logger log = LoggerFactory.getLogger(McasRetrocesionService.class);
    public static final int DIAS_PERMANENCIA = 365;
    private static final List<String> CUOTAS = List.of("primer", "segundo", "tercer", "cuarto", "quinto",
            "sexto", "septimo", "octavo", "noveno", "decimo", "decimoprimer", "decimosegundo");

    private final McasRetrocesionRepository repo;
    private final McasPropuestaRepository propuestaRepo;
    private final McasPoliticaRetrocesionRepository politicaRepo;
    private final McasTramoRetrocesionRepository tramoRepo;
    private final McasRetrocesionPolicyService politicas;
    private final ContratoRepository contratoRepo;
    private final PuntoSuministroRepository puntoRepo;
    private final ServicioRepository servicioRepo;
    private final ServiciosPagosRepository serviciosPagosRepo;
    private final TBPagoPlazosRepository pagoPlazosRepo;
    private final TBComercializadorasRepository comercializadoraRepo;
    private final ModuloConfiguracionService flags;
    private final McasAuditoriaService auditoria;

    /** Fila para Comisiones MCAS, con los nombres ya resueltos. */
    public record Vista(McasRetrocesion retrocesion, String cliente, String numeroContrato, String cups,
                        String comercializadora, String politica) {}

    private record Fin(LocalDate fecha, String motivo, Integer idContratoNuevo) {}

    private record CuotasAnulables(List<Integer> numeros, BigDecimal importe) {}

    public boolean activo() {
        return flags.estaActivo(McasProposalService.FLAG_ENABLED) && flags.estaActivo(McasProposalService.FLAG_RETROCESION);
    }

    // ============================================================
    // DETECCIÓN
    // ============================================================

    /**
     * Mira los contratos del punto con la comisión aprobada y deja al día sus retrocesiones
     * pendientes: crea la que falte, corrige la que cambie de fecha y descarta sola la que ya no
     * proceda (p. ej. el punto se reactivó). Las aprobadas o descartadas a mano no se tocan.
     */
    @Transactional
    public List<McasRetrocesion> revisarPunto(Integer idPunto) {
        if (idPunto == null || !activo()) return List.of();
        PuntoSuministro punto = puntoRepo.findById(idPunto).orElse(null);
        List<Contrato> contratos = contratoRepo.findByPuntoSuministroId(idPunto).stream()
                .filter(c -> c.getFechaInicio() != null)
                .sorted(Comparator.comparing((Contrato c) -> FechaNegocio.aLocalDate(c.getFechaInicio()))
                        .thenComparing(Contrato::getId))
                .toList();
        List<McasRetrocesion> tocadas = new ArrayList<>();
        // Un contrato borrado no llegó a hacerse: su retrocesión tampoco, ni el servicio en negativo
        // que creara (el de la comisión ya lo borra McasProposalService al borrar el contrato)
        for (McasRetrocesion huerfana : repo.findByIdPuntoSuministro(idPunto)) {
            if (contratoRepo.existsById(huerfana.getIdContrato())) continue;
            Integer idServicio = huerfana.getIdServicioCreado();
            if (idServicio != null) {
                serviciosPagosRepo.findByServicioId(idServicio).ifPresent(serviciosPagosRepo::delete);
                if (servicioRepo.existsById(idServicio)) servicioRepo.deleteById(idServicio);
            }
            repo.delete(huerfana);
        }
        for (int i = 0; i < contratos.size(); i++) {
            Contrato c = contratos.get(i);
            Optional<McasPropuesta> comision = comisionAprobada(c.getId());
            if (comision.isEmpty()) continue;
            Optional<McasRetrocesion> previa = repo.findByIdPropuesta(comision.get().getId());
            if (previa.isPresent() && !McasRetrocesion.PENDIENTE.equals(previa.get().getEstado())) continue;

            LocalDate inicio = FechaNegocio.aLocalDate(c.getFechaInicio());
            Fin fin = finEfectivo(c, i + 1 < contratos.size() ? contratos.get(i + 1) : null, punto);
            long dias = fin == null ? DIAS_PERMANENCIA : Math.max(0, ChronoUnit.DAYS.between(inicio, fin.fecha()));
            if (fin == null || dias >= DIAS_PERMANENCIA) {
                previa.ifPresent(r -> tocadas.add(cerrar(r, McasRetrocesion.DESCARTADA,
                        "Ya no procede: el contrato vuelve a estar activo o ha cumplido los 365 días.", "sistema")));
                continue;
            }
            McasRetrocesion r = previa.orElseGet(() -> McasRetrocesion.builder()
                    .idPropuesta(comision.get().getId()).idContrato(c.getId()).build());
            r.setIdPuntoSuministro(idPunto);
            r.setIdComercializadora(c.getIdComercializadoraActual());
            r.setMotivo(fin.motivo());
            r.setIdContratoNuevo(fin.idContratoNuevo());
            r.setFechaInicio(inicio);
            r.setFechaFin(fin.fecha());
            r.setDiasActivo((int) dias);
            calcular(r, comision.get());
            boolean nueva = r.getId() == null;
            McasRetrocesion guardada = repo.save(r);
            tocadas.add(guardada);
            if (nueva) {
                auditoria.registrar(comision.get().getId(), c.getId(), "RETROCESION_DETECTADA", null, McasRetrocesion.PENDIENTE,
                        datos(guardada), "sistema", null);
                log.info("MCAS contrato={} propuesta={}: retrocesión pendiente por {} a los {} días",
                        c.getId(), comision.get().getId(), fin.motivo(), dias);
            }
        }
        return tocadas;
    }

    /** La comisión del contrato que llegó a crear su servicio (la última, si hubiera varias). */
    private Optional<McasPropuesta> comisionAprobada(Integer idContrato) {
        return propuestaRepo.findActivasPorContrato(idContrato).stream()
                .filter(p -> p.getIdServicioCreado() != null)
                .findFirst();
    }

    /**
     * Cuándo y por qué dejó de estar con la comercializadora. Si detrás hay otro contrato en el
     * mismo punto manda ese: con la misma comercializadora es una renovación (no hay
     * retrocesión, aunque la renovación ponga la baja al original) y con otra es un cambio, que
     * cuenta desde que empieza el nuevo. Sin contrato detrás, la baja del contrato o del punto.
     */
    private Fin finEfectivo(Contrato c, Contrato siguiente, PuntoSuministro punto) {
        // Un contrato posterior sin comercializadora (a medio rellenar) no dice nada: se mira la baja
        if (siguiente != null && c.getIdComercializadoraActual() != null && siguiente.getIdComercializadoraActual() != null) {
            if (Objects.equals(c.getIdComercializadoraActual(), siguiente.getIdComercializadoraActual()))
                return null;
            return new Fin(FechaNegocio.aLocalDate(siguiente.getFechaInicio()),
                    McasRetrocesion.CAMBIO_COMERCIALIZADORA, siguiente.getId());
        }
        if (BajaEfectiva.tieneBaja(c.getFechaBaja()))
            return new Fin(FechaNegocio.aLocalDate(c.getFechaBaja()), McasRetrocesion.BAJA_CONTRATO, null);
        if (punto != null && BajaEfectiva.tieneBaja(punto.getFechaBaja()))
            return new Fin(FechaNegocio.aLocalDate(punto.getFechaBaja()), McasRetrocesion.BAJA_PUNTO, null);
        return null;
    }

    // ============================================================
    // CÁLCULO
    // ============================================================

    private void calcular(McasRetrocesion r, McasPropuesta comision) {
        BigDecimal total = Optional.ofNullable(comision.getImporteDefinitivo())
                .or(() -> Optional.ofNullable(comision.getImporteCalculado())).orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        CuotasAnulables cuotas = cuotasAnulables(comision.getIdServicioCreado(), r.getFechaFin());
        // Lo que se cobra de la comisión: lo que no se va a anular. Nunca se devuelve más.
        BigDecimal base = total.subtract(cuotas.importe()).max(BigDecimal.ZERO);
        r.setImporteComision(total);
        r.setCuotasAnuladas(cuotas.numeros().isEmpty() ? null
                : cuotas.numeros().stream().map(String::valueOf).collect(Collectors.joining(",")));
        r.setImporteCuotasAnuladas(cuotas.importe());
        r.setIdPolitica(comision.getIdPoliticaRetrocesion());

        McasPoliticaRetrocesion politica = comision.getIdPoliticaRetrocesion() == null ? null
                : politicaRepo.findById(comision.getIdPoliticaRetrocesion()).orElse(null);
        if (politica == null) {
            sinImporte(r, "La comisión no tiene política de retrocesión: escriba el importe a mano al aprobarla.");
            return;
        }
        String metodo = politica.getMetodo() == null ? "" : politica.getMetodo().toUpperCase();
        if ("NO_APLICA".equals(metodo)) {
            conImporte(r, BigDecimal.ZERO, base, false, "La política dice que no aplica retrocesión.");
            return;
        }
        if ("PRORRATEO".equals(metodo)) {
            int quedan = DIAS_PERMANENCIA - r.getDiasActivo();
            conImporte(r, base.multiply(BigDecimal.valueOf(quedan)).divide(BigDecimal.valueOf(DIAS_PERMANENCIA), 2, RoundingMode.HALF_UP),
                    base, limita(politica), "Prorrateo: " + quedan + " de 365 días sin cumplir sobre " + euros(base) + ".");
            return;
        }
        long meses = ChronoUnit.MONTHS.between(r.getFechaInicio(), r.getFechaFin());
        Optional<McasTramoRetrocesion> tramo = tramoRepo
                .findByIdPoliticaOrderByUnidadAscDesdeValorAscPrioridadAsc(politica.getId()).stream()
                .filter(t -> dentro(t, "DIAS".equalsIgnoreCase(t.getUnidad()) ? r.getDiasActivo() : meses))
                .min(Comparator.comparing(t -> t.getPrioridad() == null ? 100 : t.getPrioridad()));
        if (tramo.isEmpty()) {
            sinImporte(r, "Ningún tramo de la política cubre una baja a los " + r.getDiasActivo()
                    + " días: escriba el importe a mano al aprobarla.");
            return;
        }
        McasTramoRetrocesion t = tramo.get();
        String rango = "tramo de " + t.getDesdeValor() + (t.getHastaValor() != null ? " a " + t.getHastaValor() : " en adelante")
                + ("DIAS".equalsIgnoreCase(t.getUnidad()) ? " días" : " meses");
        switch (t.getTipoValor() == null ? "" : t.getTipoValor().toUpperCase()) {
            case "PORCENTAJE", "FACTOR" -> conImporte(r, base.multiply(t.getValor()).setScale(2, RoundingMode.HALF_UP), base,
                    limita(politica), porcentaje(t.getValor()) + " de " + euros(base) + " (" + rango + ").");
            case "IMPORTE_FIJO" -> conImporte(r, t.getValor().setScale(2, RoundingMode.HALF_UP), base,
                    limita(politica), "Importe fijo del " + rango + ".");
            default -> sinImporte(r, "El " + rango + " no tiene elegido el tipo de valor: escriba el importe a mano al aprobarla.");
        }
    }

    private boolean limita(McasPoliticaRetrocesion p) {
        return !Boolean.FALSE.equals(p.getLimiteImportePagado());
    }

    private void conImporte(McasRetrocesion r, BigDecimal importe, BigDecimal base, boolean limitar, String detalle) {
        boolean recortado = limitar && importe.compareTo(base) > 0;
        r.setImporteCalculado(recortado ? base : importe);
        r.setDetalle(detalle + (recortado ? " Se queda en lo cobrado." : "") + textoCuotas(r));
    }

    private void sinImporte(McasRetrocesion r, String detalle) {
        r.setImporteCalculado(null);
        r.setDetalle(detalle + textoCuotas(r));
    }

    private String textoCuotas(McasRetrocesion r) {
        if (r.getCuotasAnuladas() == null) return "";
        return " Al aprobarla se anulan las cuotas sin cobrar posteriores a la baja (" + r.getCuotasAnuladas()
                + "), que suman " + euros(r.getImporteCuotasAnuladas()) + ".";
    }

    private boolean dentro(McasTramoRetrocesion t, long valor) {
        boolean desde = !Boolean.FALSE.equals(t.getDesdeInclusivo()) ? valor >= t.getDesdeValor() : valor > t.getDesdeValor();
        boolean hasta = t.getHastaValor() == null
                || (Boolean.TRUE.equals(t.getHastaInclusivo()) ? valor <= t.getHastaValor() : valor < t.getHastaValor());
        return desde && hasta;
    }

    /** Cuotas del servicio de la comisión sin cobrar y con fecha posterior a la baja. */
    private CuotasAnulables cuotasAnulables(Integer idServicio, LocalDate fin) {
        List<Integer> numeros = new ArrayList<>();
        BigDecimal suma = BigDecimal.ZERO;
        Optional<ServiciosPagos> plan = idServicio == null ? Optional.empty() : serviciosPagosRepo.findByServicioId(idServicio);
        if (plan.isPresent()) {
            BeanWrapper cuota = new BeanWrapperImpl(plan.get());
            for (int i = 0; i < CUOTAS.size(); i++) {
                String prefijo = CUOTAS.get(i) + "Pago";
                Double importe = (Double) cuota.getPropertyValue(prefijo + "Importe");
                LocalDate fecha = FechaNegocio.aLocalDate((java.util.Date) cuota.getPropertyValue(prefijo + "Fecha"));
                if (importe == null || importe == 0.0 || fecha == null || !fecha.isAfter(fin)) continue;
                if (Boolean.TRUE.equals(cuota.getPropertyValue(prefijo + "Pagado"))) continue;
                numeros.add(i + 1);
                suma = suma.add(BigDecimal.valueOf(importe));
            }
        }
        return new CuotasAnulables(numeros, suma.setScale(2, RoundingMode.HALF_UP));
    }

    // ============================================================
    // APROBAR Y DESCARTAR (administrador)
    // ============================================================

    /**
     * Aprueba la retrocesión: anula las cuotas sin cobrar posteriores a la baja en el servicio de
     * la comisión y, si hay importe que devolver, crea un servicio en negativo con un solo pago.
     * Sin importe se usa el calculado; si tampoco lo hay, hay que escribirlo.
     */
    @Transactional
    public McasRetrocesion aprobar(Long id, BigDecimal importe) {
        McasRetrocesion r = pendiente(id);
        BigDecimal definitivo = importe != null ? importe : r.getImporteCalculado();
        if (definitivo == null)
            throw new IllegalArgumentException("Escriba el importe de la retrocesión: no se ha podido calcular solo.");
        if (definitivo.signum() < 0)
            throw new IllegalArgumentException("El importe de la retrocesión se escribe en positivo (lo que se devuelve).");
        definitivo = definitivo.setScale(2, RoundingMode.HALF_UP);
        McasPropuesta comision = propuestaRepo.findById(r.getIdPropuesta())
                .orElseThrow(() -> new IllegalArgumentException("La comisión de esta retrocesión ya no existe."));
        Servicio servicioComision = comision.getIdServicioCreado() == null ? null
                : servicioRepo.findById(comision.getIdServicioCreado()).orElse(null);
        String usuario = usuario();

        CuotasAnulables anuladas = anularCuotas(servicioComision, r, usuario);
        r.setCuotasAnuladas(anuladas.numeros().isEmpty() ? null
                : anuladas.numeros().stream().map(String::valueOf).collect(Collectors.joining(",")));
        r.setImporteCuotasAnuladas(anuladas.importe());
        r.setImporteDefinitivo(definitivo);
        if (definitivo.signum() > 0)
            r.setIdServicioCreado(crearServicio(r, comision, servicioComision, definitivo, usuario).getId());
        McasRetrocesion cerrada = cerrar(r, McasRetrocesion.APROBADA, null, usuario);
        auditoria.registrar(comision.getId(), r.getIdContrato(), "RETROCESION_APROBADA", McasRetrocesion.PENDIENTE,
                McasRetrocesion.APROBADA, datos(cerrada), usuario, null);
        return cerrada;
    }

    @Transactional
    public McasRetrocesion descartar(Long id, String motivo) {
        McasRetrocesion r = pendiente(id);
        if (motivo == null || motivo.isBlank())
            throw new IllegalArgumentException("Escriba el motivo por el que no procede la retrocesión.");
        String usuario = usuario();
        McasRetrocesion cerrada = cerrar(r, McasRetrocesion.DESCARTADA, motivo.trim(), usuario);
        auditoria.registrar(r.getIdPropuesta(), r.getIdContrato(), "RETROCESION_DESCARTADA", McasRetrocesion.PENDIENTE,
                McasRetrocesion.DESCARTADA, datos(cerrada), usuario, null);
        return cerrada;
    }

    private McasRetrocesion pendiente(Long id) {
        McasRetrocesion r = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la retrocesión #" + id + "."));
        if (!McasRetrocesion.PENDIENTE.equals(r.getEstado()))
            throw new IllegalArgumentException("Esta retrocesión ya está "
                    + (McasRetrocesion.APROBADA.equals(r.getEstado()) ? "aprobada" : "descartada") + ": recargue la lista.");
        return r;
    }

    private McasRetrocesion cerrar(McasRetrocesion r, String estado, String motivoDescarte, String usuario) {
        r.setEstado(estado);
        if (motivoDescarte != null)
            r.setMotivoDescarte(motivoDescarte.length() > 500 ? motivoDescarte.substring(0, 500) : motivoDescarte);
        r.setResueltoPor(usuario);
        r.setResueltoEn(LocalDateTime.now());
        return repo.save(r);
    }

    private CuotasAnulables anularCuotas(Servicio servicio, McasRetrocesion r, String usuario) {
        if (servicio == null) return new CuotasAnulables(List.of(), BigDecimal.ZERO.setScale(2));
        CuotasAnulables cuotas = cuotasAnulables(servicio.getId(), r.getFechaFin());
        if (cuotas.numeros().isEmpty()) return cuotas;
        double resta = cuotas.importe().doubleValue();
        serviciosPagosRepo.findByServicioId(servicio.getId()).ifPresent(plan -> {
            BeanWrapper cuota = new BeanWrapperImpl(plan);
            for (Integer numero : cuotas.numeros())
                cuota.setPropertyValue(CUOTAS.get(numero - 1) + "PagoImporte", 0.0);
            if (plan.getImporte() != null) plan.setImporte(redondear(plan.getImporte() - resta));
            if (plan.getImporteFinal() != null) plan.setImporteFinal(redondear(plan.getImporteFinal() - resta));
            serviciosPagosRepo.save(plan);
        });
        if (servicio.getImporte() != null) servicio.setImporte(redondear(servicio.getImporte() - resta));
        if (servicio.getImporteFinal() != null) servicio.setImporteFinal(redondear(servicio.getImporteFinal() - resta));
        servicio.setObservaciones(unir(servicio.getObservaciones(), "Cuotas " + cuotas.numeros().stream()
                .map(String::valueOf).collect(Collectors.joining(", ")) + " anuladas (" + euros(cuotas.importe())
                + ") por la baja del " + r.getFechaFin() + ". Aprobado por: " + usuario + "."));
        servicioRepo.save(servicio);
        return cuotas;
    }

    private Servicio crearServicio(McasRetrocesion r, McasPropuesta comision, Servicio origen, BigDecimal importe, String usuario) {
        Contrato c = contratoRepo.findById(r.getIdContrato())
                .orElseThrow(() -> new IllegalArgumentException("El contrato de esta retrocesión ya no existe."));
        TBPagoPlazos plazos = pagoPlazosRepo.findFirstByNumeroCuotas(1)
                .orElseThrow(() -> new IllegalStateException("No existe una modalidad de pago de 1 cuota: "
                        + "créela en Administración, Pago Plazos, antes de aprobar la retrocesión."));
        double negativo = -importe.doubleValue();
        java.sql.Date hoy = new java.sql.Date(System.currentTimeMillis());
        Servicio s = new Servicio();
        s.setFecha(hoy);
        s.setCliente(c.getCliente());
        s.setPuntoSuministro(c.getPuntoSuministro());
        s.setDescripcion("MCAS Retrocesión - Propuesta #" + comision.getId());
        s.setIdComercializadora(c.getIdComercializadoraActual());
        s.setIdGestora(c.getMcasIdGestora());
        if (origen != null) { s.setIdProducto(origen.getIdProducto()); s.setCups(origen.getCups()); }
        s.setIdPagoPlazos(plazos.getId());
        s.setImporte(negativo);
        s.setImporteFinal(negativo);
        s.setDescuento(0.0);
        s.setFechaInicioServicio(java.sql.Date.valueOf(r.getFechaFin()));
        s.setObservaciones("Retrocesión de la comisión de la propuesta #" + comision.getId() + " ("
                + textoMotivo(r.getMotivo()) + " el " + r.getFechaFin() + ", a los " + r.getDiasActivo() + " días). "
                + (r.getDetalle() != null ? r.getDetalle() + " " : "") + "Aprobada por: " + usuario + ".");
        s = servicioRepo.save(s);
        ServiciosPagos sp = new ServiciosPagos();
        sp.setServicio(s);
        sp.setImporte(negativo);
        sp.setImporteFinal(negativo);
        sp.setDescuento(0.0);
        sp.setPagoPlazos(plazos);
        sp.setPrimerPagoFecha(hoy);
        sp.setPrimerPagoImporte(negativo);
        sp.setPrimerPagoPagado(false);
        serviciosPagosRepo.save(sp);
        return s;
    }

    // ============================================================
    // CONSULTA
    // ============================================================

    @Transactional(readOnly = true)
    public List<Vista> listar() {
        List<McasRetrocesion> todas = repo.findAllByOrderByCreadoEnDescIdDesc();
        Map<Integer, String> politicasTexto = politicas.describirTodas(todas.stream()
                .map(McasRetrocesion::getIdPolitica).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Integer, String> comercializadoras = new LinkedHashMap<>();
        List<Vista> vistas = new ArrayList<>();
        for (McasRetrocesion r : todas) {
            Contrato c = contratoRepo.findById(r.getIdContrato()).orElse(null);
            PuntoSuministro ps = c != null ? c.getPuntoSuministro() : null;
            String cups = ps == null ? null : (ps.getCUPSluz() != null && !ps.getCUPSluz().isBlank() ? ps.getCUPSluz() : ps.getCUPSgas());
            String comercializadora = r.getIdComercializadora() == null ? null
                    : comercializadoras.computeIfAbsent(r.getIdComercializadora(), id -> comercializadoraRepo.findById(id)
                            .map(x -> x.getNombre()).orElse("Comercializadora #" + id));
            vistas.add(new Vista(r, c != null && c.getCliente() != null ? c.getCliente().getNombre() : null,
                    c != null ? c.getContrato() : null, cups, comercializadora,
                    r.getIdPolitica() != null ? politicasTexto.get(r.getIdPolitica()) : null));
        }
        return vistas;
    }

    public long pendientes() {
        return repo.countByEstado(McasRetrocesion.PENDIENTE);
    }

    public static String textoMotivo(String motivo) {
        if (McasRetrocesion.CAMBIO_COMERCIALIZADORA.equals(motivo)) return "cambio de comercializadora";
        if (McasRetrocesion.BAJA_PUNTO.equals(motivo)) return "baja del punto de suministro";
        return "baja del contrato";
    }

    private Map<String, Object> datos(McasRetrocesion r) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id_retrocesion", r.getId());
        datos.put("motivo", r.getMotivo());
        datos.put("fecha_fin", String.valueOf(r.getFechaFin()));
        datos.put("dias_activo", r.getDiasActivo());
        datos.put("importe_calculado", r.getImporteCalculado() != null ? r.getImporteCalculado() : "");
        datos.put("importe_definitivo", r.getImporteDefinitivo() != null ? r.getImporteDefinitivo() : "");
        datos.put("cuotas_anuladas", r.getCuotasAnuladas() != null ? r.getCuotasAnuladas() : "");
        datos.put("id_servicio", r.getIdServicioCreado() != null ? r.getIdServicioCreado() : "");
        datos.put("motivo_descarte", r.getMotivoDescarte() != null ? r.getMotivoDescarte() : "");
        return datos;
    }

    private static double redondear(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String unir(String a, String b) {
        return a == null || a.isBlank() ? b : a + " " + b;
    }

    private static String euros(BigDecimal v) {
        return (v == null ? "0,00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')) + " €";
    }

    private static String porcentaje(BigDecimal factor) {
        return factor.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString().replace('.', ',') + " %";
    }

    private String usuario() {
        String u = AuraUserHolder.get();
        return u != null ? u : "sistema";
    }
}
