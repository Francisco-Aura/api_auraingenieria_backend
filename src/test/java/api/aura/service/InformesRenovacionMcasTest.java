package api.aura.service;

import api.aura.dto.informes.ContratoPendienteRenovacionDTO;
import api.aura.entity.Contrato;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.InformesRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.repository.tablas_maestras.TBSectoresRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que se cambia de un contrato desde el informe de contratos pendientes de renovación
 * (número, tarifa, comercializadora...) se guarda como desde su formulario: MCAS recalcula
 * la comisión y la última relación con la comercializadora (antes la propuesta se quedaba
 * desactualizada y al aprobarla pedía recalcular).
 */
class InformesRenovacionMcasTest {

    private final ContratoRepository contratos = mock(ContratoRepository.class);
    private final ContratoService contratoService = mock(ContratoService.class);
    private final InformesService servicio = new InformesService(mock(InformesRepository.class), contratos,
            mock(PuntoSuministroRepository.class), mock(ClienteRepository.class), mock(TBSectoresRepository.class));

    @Test
    void los_cambios_del_informe_pasan_por_el_guardado_del_contrato() {
        Contrato contrato = Contrato.builder().id(12).contrato("CT-1").build();
        when(contratos.findById(12)).thenReturn(Optional.of(contrato));
        ReflectionTestUtils.setField(servicio, "contratoService", contratoService);
        ContratoPendienteRenovacionDTO dto = new ContratoPendienteRenovacionDTO();
        dto.setContrato("CT-2");

        servicio.actualizarContratoPendienteRenovacion(12, dto);

        assertThat(contrato.getContrato()).isEqualTo("CT-2");
        verify(contratoService).guardarCambiosDeOtraPantalla(contrato);
        verify(contratos, never()).save(any());
    }
}
