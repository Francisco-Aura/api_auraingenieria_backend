package api.aura.controller;

import api.aura.entity.ServiciosPagos;
import api.aura.service.ServiciosPagosService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.text.SimpleDateFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fecha de arranque del plan de cuotas.
 *
 * <p>El parseo iba en modo indulgente: "2026-02-30" no fallaba, se convertia en el
 * 2 de marzo y el plan entero salia con las fechas corridas sin que nadie lo viera.</p>
 */
@ExtendWith(MockitoExtension.class)
class PlanPagosFechaTest {

    @Mock
    private ServiciosPagosService serviciosPagosService;

    @InjectMocks
    private ServiciosPagosController controlador;

    private Map<String, Object> peticion(String fecha) {
        return Map.of("idPagoPlazos", 3, "importeTotal", 240.0, "fechaInicio", fecha);
    }

    @Test
    void una_fecha_inexistente_no_se_corre_al_mes_siguiente() {
        ResponseEntity<?> respuesta = controlador.generarPlanPagos(peticion("2026-02-30"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        verify(serviciosPagosService, never())
                .generarCuotasSegunModalidad(any(), anyInt(), any());
    }

    @Test
    void una_fecha_correcta_llega_intacta_al_calculo() throws Exception {
        when(serviciosPagosService.generarCuotasSegunModalidad(any(), anyInt(), any()))
                .thenReturn(new ServiciosPagos());

        ResponseEntity<?> respuesta = controlador.generarPlanPagos(peticion("2026-01-31"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<ServiciosPagos> plantilla = ArgumentCaptor.forClass(ServiciosPagos.class);
        verify(serviciosPagosService).generarCuotasSegunModalidad(plantilla.capture(), anyInt(), any());
        assertThat(new SimpleDateFormat("yyyy-MM-dd").format(plantilla.getValue().getPrimerPagoFecha()))
                .isEqualTo("2026-01-31");
    }
}
