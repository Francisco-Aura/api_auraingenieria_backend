package api.aura.service;

import api.aura.entity.Contrato;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.service.mcas.McasProposalService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContratoRenovacionTest {

    private final ContratoRepository contratos = mock(ContratoRepository.class);
    private final ContratoService servicio = new ContratoService(contratos, mock(ClienteRepository.class),
            mock(PuntoSuministroRepository.class), mock(ContratoFileStorageService.class),
            mock(McasProposalService.class), mock(ApplicationEventPublisher.class));

    private Contrato renovar(Contrato original) {
        when(contratos.findById(original.getId())).thenReturn(Optional.of(original));
        when(contratos.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));
        servicio.renovarContrato(original.getId(), Contrato.builder().contrato("CT-NUEVO").build());
        return original;
    }

    @Test
    void renovar_un_contrato_activo_lo_marca_como_vencido_hoy() {
        Contrato original = renovar(Contrato.builder().id(7).contrato("CT-1").build());

        assertThat(original.getFechaBaja()).isEqualTo(java.sql.Date.valueOf(LocalDate.now()));
    }

    @Test
    void renovar_un_contrato_que_ya_estaba_de_baja_conserva_su_fecha_de_baja() {
        java.sql.Date bajaAnterior = java.sql.Date.valueOf("2025-03-03");

        Contrato original = renovar(Contrato.builder().id(8).contrato("CT-2").fechaBaja(bajaAnterior).build());

        assertThat(original.getFechaBaja()).isEqualTo(bajaAnterior);
    }

    @Test
    void las_fechas_centinela_cuentan_como_contrato_activo() {
        // 0001-01-01 y 1900-01-01 equivalen a «sin baja» en informes y pantallas
        Contrato original = renovar(Contrato.builder().id(9).contrato("CT-3")
                .fechaBaja(java.sql.Date.valueOf("1900-01-01")).build());

        assertThat(original.getFechaBaja()).isEqualTo(java.sql.Date.valueOf(LocalDate.now()));
    }
}
