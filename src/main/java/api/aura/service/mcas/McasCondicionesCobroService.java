package api.aura.service.mcas;

import api.aura.config.AuraUserHolder;
import api.aura.entity.mcas.McasCondicionCobro;
import api.aura.entity.mcas.McasCondicionCobro.DiaCobro;
import api.aura.repository.mcas.McasCondicionCobroRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.repository.tablas_maestras.TBGestorasRepository;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Condiciones de cobro de las comisiones (respuestas del cliente del 05/11/2026, dudas 8 y 9):
 * cada comercializadora cobra en un número de pagos y el primero llega un tiempo después de la
 * fecha de inicio del contrato (normalmente a final de mes o el día 1 del siguiente; con una
 * gestora como Nexo, varios meses después). Las de una gestora ganan a las generales.
 *
 * <p>Es la única pieza que decide el número de pagos «de la comercializadora» y las fechas de
 * las cuotas: la usan la propuesta, la vista previa, la simulación y el traslado de cuotas
 * cuando cambia la fecha de inicio, para que las cuatro den siempre lo mismo.
 *
 * <p>Sin condiciones configuradas se mantiene el comportamiento anterior: el primer cobro en la
 * fecha de inicio y una cuota cada mes.
 */
@Service
public class McasCondicionesCobroService {

    static final int PAGOS_MAX = 12;
    static final int MESES_MAX = 24;

    private final McasCondicionCobroRepository repo;
    private final TBComercializadorasRepository comercializadoras;
    private final TBGestorasRepository gestoras;
    private final TBPagoPlazosRepository pagoPlazos;

    @PersistenceContext
    private EntityManager em;

    public McasCondicionesCobroService(McasCondicionCobroRepository repo,
                                       TBComercializadorasRepository comercializadoras,
                                       TBGestorasRepository gestoras, TBPagoPlazosRepository pagoPlazos) {
        this.repo = repo;
        this.pagoPlazos = pagoPlazos;
        this.comercializadoras = comercializadoras;
        this.gestoras = gestoras;
    }

    // ------------------------------------------------------------------ consulta

    public List<McasCondicionCobro> listar() {
        return repo.findAll(Sort.by("idComercializadora", "idGestora"));
    }

    /**
     * Las condiciones que se aplican a un contrato de esa comercializadora y gestora: las de la
     * gestora si las tiene; si no, las generales de la comercializadora.
     */
    public Optional<McasCondicionCobro> resolver(Integer idComercializadora, Integer idGestora) {
        if (idComercializadora == null) return Optional.empty();
        List<McasCondicionCobro> suyas = repo.findByIdComercializadora(idComercializadora);
        Optional<McasCondicionCobro> deLaGestora = idGestora == null ? Optional.empty()
                : suyas.stream().filter(c -> idGestora.equals(c.getIdGestora())).findFirst();
        return deLaGestora.or(() -> suyas.stream().filter(c -> c.getIdGestora() == null).findFirst());
    }

    /** Número de pagos de la comercializadora (o su gestora); null si no lo tiene configurado. */
    public Integer numeroPagos(Integer idComercializadora, Integer idGestora) {
        return resolver(idComercializadora, idGestora).map(McasCondicionCobro::getNumeroPagos).orElse(null);
    }

    /**
     * Fechas de las {@code n} cuotas de una comisión de un contrato que empieza el día
     * {@code fechaInicio}. Sin fecha de inicio, cuotas sin fecha.
     */
    public List<LocalDate> fechasCobro(Integer idComercializadora, Integer idGestora, LocalDate fechaInicio, int n) {
        Optional<McasCondicionCobro> condiciones = resolver(idComercializadora, idGestora);
        int meses = condiciones.map(McasCondicionCobro::getMesesHastaPrimerCobro).orElse(0);
        DiaCobro dia = condiciones.map(McasCondicionCobro::getDiaPrimerCobro).orElse(DiaCobro.MISMO_DIA);
        return fechasCobro(fechaInicio, meses, dia, n);
    }

    /**
     * Cuota {@code i} (0, 1, 2…): la fecha de inicio más {@code meses + i} meses, ajustada al día
     * pedido. Cada cuota se calcula desde la fecha de inicio y no desde la anterior: con inicio
     * el 31/01 salen 28/02, 31/03, 30/04… y no 28/02, 28/03, 28/04.
     */
    static List<LocalDate> fechasCobro(LocalDate fechaInicio, int meses, DiaCobro dia, int n) {
        int cuotas = Math.max(1, n);
        if (fechaInicio == null) return Collections.nCopies(cuotas, null);
        List<LocalDate> fechas = new ArrayList<>(cuotas);
        for (int i = 0; i < cuotas; i++) {
            LocalDate mes = fechaInicio.plusMonths((long) meses + i);
            fechas.add(switch (dia == null ? DiaCobro.MISMO_DIA : dia) {
                case MISMO_DIA -> mes;
                case ULTIMO_DIA_MES -> mes.with(TemporalAdjusters.lastDayOfMonth());
                case DIA_1 -> mes.withDayOfMonth(1);
            });
        }
        return fechas;
    }

    /**
     * Comisiones pendientes de aprobar de contratos de esa comercializadora (y gestora, si la
     * tiene) que se calcularon antes de cambiar sus condiciones: su calendario es el de antes
     * y al aprobarlas se crearía así. Igual que con una nueva versión de una tabla, no se
     * recalculan solas: se avisa para recalcularlas en Comisiones MCAS.
     */
    public long comisionesPendientesAfectadas(Integer idComercializadora, Integer idGestora) {
        if (idComercializadora == null || em == null) return 0;
        String jpql = "SELECT COUNT(p) FROM McasPropuesta p, Contrato c WHERE c.id = p.idContrato"
                + " AND p.estado = 'PENDIENTE_VALIDACION' AND c.idComercializadoraActual = :comercializadora"
                + (idGestora != null ? " AND c.mcasIdGestora = :gestora" : "");
        var consulta = em.createQuery(jpql, Long.class).setParameter("comercializadora", idComercializadora);
        if (idGestora != null) consulta.setParameter("gestora", idGestora);
        return consulta.getSingleResult();
    }

    // ------------------------------------------------------------------ alta, cambio y baja

    @Transactional
    public McasCondicionCobro crear(McasCondicionCobro datos) {
        return guardar(new McasCondicionCobro(), datos);
    }

    @Transactional
    public McasCondicionCobro actualizar(Integer id, McasCondicionCobro datos) {
        McasCondicionCobro actual = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Esas condiciones de cobro ya no existen."));
        return guardar(actual, datos);
    }

    /** Borra las condiciones y devuelve las que había (para avisar de las pendientes afectadas). */
    @Transactional
    public McasCondicionCobro eliminar(Integer id) {
        McasCondicionCobro borrada = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Esas condiciones de cobro ya no existen."));
        repo.delete(borrada);
        return borrada;
    }

    private McasCondicionCobro guardar(McasCondicionCobro destino, McasCondicionCobro datos) {
        validar(destino.getId(), datos);
        destino.setIdComercializadora(datos.getIdComercializadora());
        destino.setIdGestora(datos.getIdGestora());
        destino.setNumeroPagos(datos.getNumeroPagos());
        destino.setMesesHastaPrimerCobro(Objects.requireNonNullElse(datos.getMesesHastaPrimerCobro(), 0));
        destino.setDiaPrimerCobro(Objects.requireNonNullElse(datos.getDiaPrimerCobro(), DiaCobro.MISMO_DIA));
        destino.setObservaciones(datos.getObservaciones() == null || datos.getObservaciones().isBlank()
                ? null : datos.getObservaciones().trim());
        String usuario = AuraUserHolder.get();
        destino.setActualizadoPor(usuario != null ? usuario : "sistema");
        destino.setActualizadoEn(LocalDateTime.now());
        return repo.save(destino);
    }

    /** Lanza IllegalArgumentException con el motivo, con las palabras del formulario. */
    private void validar(Integer id, McasCondicionCobro datos) {
        if (datos == null) throw new IllegalArgumentException("Faltan los datos de las condiciones de cobro.");
        if (datos.getIdComercializadora() == null || !comercializadoras.existsById(datos.getIdComercializadora()))
            throw new IllegalArgumentException("Elija la comercializadora.");
        if (datos.getIdGestora() != null && !gestoras.existsById(datos.getIdGestora()))
            throw new IllegalArgumentException("La gestora elegida ya no existe.");
        if (datos.getNumeroPagos() != null && (datos.getNumeroPagos() < 1 || datos.getNumeroPagos() > PAGOS_MAX))
            throw new IllegalArgumentException("El número de pagos tiene que estar entre 1 y " + PAGOS_MAX + ".");
        // Al aprobar, el servicio necesita esa modalidad: mejor saberlo ahora que al aprobar la comisión
        if (datos.getNumeroPagos() != null && pagoPlazos.findFirstByNumeroCuotas(datos.getNumeroPagos()).isEmpty())
            throw new IllegalArgumentException("En Administración, Pago Plazos no hay ninguna modalidad de "
                    + datos.getNumeroPagos() + (datos.getNumeroPagos() == 1 ? " cuota" : " cuotas")
                    + ": créela antes, o las comisiones no se podrán aprobar.");
        Integer meses = datos.getMesesHastaPrimerCobro();
        if (meses != null && (meses < 0 || meses > MESES_MAX))
            throw new IllegalArgumentException("Los meses hasta el primer cobro tienen que estar entre 0 y " + MESES_MAX + ".");
        boolean repetida = repo.findByIdComercializadora(datos.getIdComercializadora()).stream()
                .anyMatch(otra -> !Objects.equals(otra.getId(), id) && Objects.equals(otra.getIdGestora(), datos.getIdGestora()));
        if (repetida)
            throw new IllegalArgumentException(datos.getIdGestora() == null
                    ? "Esa comercializadora ya tiene sus condiciones generales: edítelas en vez de crear otras."
                    : "Esa comercializadora ya tiene condiciones para esa gestora: edítelas en vez de crear otras.");
    }
}
