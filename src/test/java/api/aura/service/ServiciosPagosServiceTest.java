package api.aura.service;

import api.aura.entity.ServiciosPagos;
import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiciosPagosServiceTest {

    @Mock
    private TBPagoPlazosRepository pagoPlazosRepository;

    private ServiciosPagosService service;

    private static final SimpleDateFormat DIA = new SimpleDateFormat("yyyy-MM-dd");

    @BeforeEach
    void setUp() {
        service = new ServiciosPagosService();
        ReflectionTestUtils.setField(service, "pagoPlazosRepository", pagoPlazosRepository);
    }

    @Test
    void finalInstallmentReceivesResidualCentsSoInstallmentsEqualTotal() {
        TBPagoPlazos plan = TBPagoPlazos.builder().id(7).numeroCuotas(3).build();
        when(pagoPlazosRepository.findById(7)).thenReturn(Optional.of(plan));

        ServiciosPagos pagos = service.generarCuotasSegunModalidad(new ServiciosPagos(), 7, 100.00);

        assertThat(pagos.getPrimerPagoImporte()).isEqualTo(33.33);
        assertThat(pagos.getSegundoPagoImporte()).isEqualTo(33.33);
        assertThat(pagos.getTercerPagoImporte()).isEqualTo(33.34);
        assertThat(pagos.getPrimerPagoImporte()
                + pagos.getSegundoPagoImporte()
                + pagos.getTercerPagoImporte()).isEqualTo(100.00);
    }

    @Test
    void oneCentAcrossThreeInstallmentsKeepsTheCentInTheLastInstallment() {
        TBPagoPlazos plan = TBPagoPlazos.builder().id(8).numeroCuotas(3).build();
        when(pagoPlazosRepository.findById(8)).thenReturn(Optional.of(plan));

        ServiciosPagos pagos = service.generarCuotasSegunModalidad(new ServiciosPagos(), 8, 0.01);

        assertThat(pagos.getPrimerPagoImporte()).isZero();
        assertThat(pagos.getSegundoPagoImporte()).isZero();
        assertThat(pagos.getTercerPagoImporte()).isEqualTo(0.01);
    }

    @Test
    void oneInstallmentReceivesTheExactRoundedTotal() {
        TBPagoPlazos plan = TBPagoPlazos.builder().id(9).numeroCuotas(1).build();
        when(pagoPlazosRepository.findById(9)).thenReturn(Optional.of(plan));

        ServiciosPagos pagos = service.generarCuotasSegunModalidad(new ServiciosPagos(), 9, 10.015);

        assertThat(pagos.getPrimerPagoImporte()).isEqualTo(10.02);
    }

    /**
     * Cada cuota se cuenta desde la fecha de inicio, como el calendario MCAS
     * (plusMonths). Antes se sumaba un mes a la cuota anterior y el recorte de
     * febrero se arrastraba: 31/01, 28/02, 28/03, 28/04...
     */
    @Test
    void unPlanQueEmpiezaAFinDeMesNoArrastraElRecorteDeFebrero() throws Exception {
        Date inicio = DIA.parse("2026-01-31");

        assertThat(IntStream.rangeClosed(1, 12).mapToObj(n -> DIA.format(ServiciosPagosService.fechaCuota(inicio, n))))
                .containsExactly("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31",
                        "2026-06-30", "2026-07-31", "2026-08-31", "2026-09-30", "2026-10-31",
                        "2026-11-30", "2026-12-31");
    }

    /** El mismo calendario que MCAS para cualquier día de inicio (incluido un 29 en año bisiesto). */
    @Test
    void lasFechasCoincidenConElCalendarioMcas() throws Exception {
        for (String dia : List.of("2026-01-15", "2026-01-29", "2026-01-30", "2026-03-31", "2027-12-31",
                "2028-02-29", "2026-08-31")) {
            Date inicio = DIA.parse(dia);
            LocalDate base = LocalDate.parse(dia);

            assertThat(IntStream.rangeClosed(1, 12)
                            .mapToObj(n -> DIA.format(ServiciosPagosService.fechaCuota(inicio, n))))
                    .as("plan que empieza el %s", dia)
                    .containsExactlyElementsOf(IntStream.range(0, 12)
                            .mapToObj(i -> base.plusMonths(i).toString()).toList());
        }
    }

    /** El plan generado pone cada cuota a n-1 meses de la primera, sin arrastrar fechas. */
    @Test
    void elPlanGeneradoCuentaCadaCuotaDesdeLaPrimera() {
        TBPagoPlazos plan = TBPagoPlazos.builder().id(11).numeroCuotas(12).build();
        when(pagoPlazosRepository.findById(11)).thenReturn(Optional.of(plan));

        ServiciosPagos pagos = service.generarCuotasSegunModalidad(new ServiciosPagos(), 11, 120.00);

        List<Date> fechas = List.of(pagos.getPrimerPagoFecha(), pagos.getSegundoPagoFecha(),
                pagos.getTercerPagoFecha(), pagos.getCuartoPagoFecha(), pagos.getQuintoPagoFecha(),
                pagos.getSextoPagoFecha(), pagos.getSeptimoPagoFecha(), pagos.getOctavoPagoFecha(),
                pagos.getNovenoPagoFecha(), pagos.getDecimoPagoFecha(), pagos.getDecimoprimerPagoFecha(),
                pagos.getDecimosegundoPagoFecha());
        for (int n = 1; n <= 12; n++)
            assertThat(fechas.get(n - 1)).as("cuota %d", n)
                    .isEqualTo(ServiciosPagosService.fechaCuota(fechas.get(0), n));
    }

    /**
     * La primera cuota cae en la fecha de inicio del servicio que manda el formulario.
     * Antes esa fecha se borraba al vaciar las cuotas y el plan empezaba siempre hoy.
     */
    @Test
    void elPlanEmpiezaEnLaFechaDeInicioDelServicio() throws Exception {
        TBPagoPlazos plan = TBPagoPlazos.builder().id(12).numeroCuotas(3).build();
        when(pagoPlazosRepository.findById(12)).thenReturn(Optional.of(plan));
        ServiciosPagos plantilla = new ServiciosPagos();
        plantilla.setPrimerPagoFecha(DIA.parse("2026-12-15"));

        ServiciosPagos pagos = service.generarCuotasSegunModalidad(plantilla, 12, 90.00);

        assertThat(DIA.format(pagos.getPrimerPagoFecha())).isEqualTo("2026-12-15");
        assertThat(DIA.format(pagos.getSegundoPagoFecha())).isEqualTo("2027-01-15");
        assertThat(DIA.format(pagos.getTercerPagoFecha())).isEqualTo("2027-02-15");
    }

    @Test
    void sinFechaDeInicioElPlanEmpiezaHoyComoAntes() {
        TBPagoPlazos plan = TBPagoPlazos.builder().id(13).numeroCuotas(2).build();
        when(pagoPlazosRepository.findById(13)).thenReturn(Optional.of(plan));

        ServiciosPagos pagos = service.generarCuotasSegunModalidad(new ServiciosPagos(), 13, 20.00);

        assertThat(DIA.format(pagos.getPrimerPagoFecha())).isEqualTo(DIA.format(new Date()));
    }

    @Test
    void plansLongerThanTheModelAreCappedAtTwelveExactInstallments() {
        TBPagoPlazos plan = TBPagoPlazos.builder().id(10).numeroCuotas(15).build();
        when(pagoPlazosRepository.findById(10)).thenReturn(Optional.of(plan));

        ServiciosPagos pagos = service.generarCuotasSegunModalidad(new ServiciosPagos(), 10, 100.00);

        assertThat(pagos.getPrimerPagoImporte()).isEqualTo(8.33);
        assertThat(pagos.getDecimoprimerPagoImporte()).isEqualTo(8.33);
        assertThat(pagos.getDecimosegundoPagoImporte()).isEqualTo(8.37);
    }
}
