package api.aura.service;

import api.aura.entity.Cliente;
import api.aura.entity.Contrato;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.service.mcas.McasProposalService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContratoServiceConsultaTest {

    @Test
    void los_contratos_de_un_punto_se_devuelven_con_su_cliente_sin_una_segunda_consulta() {
        ContratoRepository contratos = mock(ContratoRepository.class);
        ClienteRepository clientes = mock(ClienteRepository.class);
        Cliente cliente = Cliente.builder().id(5).nombre("Cliente Demo").build();
        when(contratos.findByPuntoSuministroId(9)).thenReturn(List.of(
                Contrato.builder().id(1).cliente(cliente).build(),
                Contrato.builder().id(2).cliente(cliente).build()));
        ContratoService servicio = new ContratoService(contratos, clientes, mock(PuntoSuministroRepository.class),
                mock(ContratoFileStorageService.class), mock(McasProposalService.class),
                mock(ApplicationEventPublisher.class));

        List<Contrato> resultado = servicio.obtenerPorPuntoSuministroId(9);

        assertThat(resultado).extracting(c -> c.getCliente().getNombre()).containsOnly("Cliente Demo");
        verify(clientes, never()).findAllById(any());
    }
}
