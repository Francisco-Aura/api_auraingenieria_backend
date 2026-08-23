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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiciosPagosServiceTest {

    @Mock
    private TBPagoPlazosRepository pagoPlazosRepository;

    private ServiciosPagosService service;

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
