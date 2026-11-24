package api.aura.service;

import api.aura.entity.Cliente;
import api.aura.entity.Contrato;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.service.mcas.McasContratoGuardadoEvent;
import api.aura.service.mcas.McasProposalService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * MCAS debe ser un efecto secundario NO bloqueante del guardado del contrato:
 * al guardar un contrato listo se publica un evento que genera la propuesta
 * tras el commit (ver {@code McasContratoEventListener}); el disparo nunca
 * ocurre dentro de la transacción de guardado ni puede revertirla.
 */
class ContratoMcasIntegrationTest {

    @Test
    void guardar_contrato_listo_publica_evento_y_no_dispara_mcas_en_la_transaccion() throws Exception {
        ContratoRepository contratos = mock(ContratoRepository.class);
        ClienteRepository clientes = mock(ClienteRepository.class);
        PuntoSuministroRepository puntos = mock(PuntoSuministroRepository.class);
        ContratoFileStorageService archivos = mock(ContratoFileStorageService.class);
        McasProposalService mcas = mock(McasProposalService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ContratoService service = new ContratoService(contratos, clientes, puntos, archivos, mcas, publisher);
        Cliente cliente = Cliente.builder().id(5).build();
        Contrato contrato = contratoListo(cliente);
        when(clientes.findById(5)).thenReturn(Optional.of(cliente));
        when(contratos.save(any())).thenAnswer(inv -> {
            Contrato guardado = inv.getArgument(0); guardado.setId(42); return guardado;
        });
        when(mcas.moduloHabilitado()).thenReturn(true);

        Contrato resultado = service.guardarContrato(contrato);

        // El contrato se guarda y MCAS no se invoca síncronamente dentro del guardado.
        assertThat(resultado.getId()).isEqualTo(42);
        verify(mcas, never()).triggerDesdeContrato(anyInt());
        ArgumentCaptor<McasContratoGuardadoEvent> captor = ArgumentCaptor.forClass(McasContratoGuardadoEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getIdContrato()).isEqualTo(42);
        // El guardado sigue siendo transaccional.
        assertThat(ContratoService.class.getMethod("guardarContrato", Contrato.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void guardar_borrador_no_dispara_mcas_hasta_que_el_contrato_este_listo() {
        ContratoRepository contratos = mock(ContratoRepository.class);
        ClienteRepository clientes = mock(ClienteRepository.class);
        PuntoSuministroRepository puntos = mock(PuntoSuministroRepository.class);
        ContratoFileStorageService archivos = mock(ContratoFileStorageService.class);
        McasProposalService mcas = mock(McasProposalService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ContratoService service = new ContratoService(contratos, clientes, puntos, archivos, mcas, publisher);
        Cliente cliente = Cliente.builder().id(5).build();
        Contrato borrador = Contrato.builder()
                .cliente(cliente)
                .mcasTipoPrecio("FIJO")
                .mcasTipoSuministro("LUZ")
                .build();
        when(clientes.findById(5)).thenReturn(Optional.of(cliente));
        when(contratos.save(any())).thenAnswer(inv -> {
            Contrato guardado = inv.getArgument(0); guardado.setId(42); return guardado;
        });

        service.guardarContrato(borrador);

        verifyNoInteractions(mcas);
        verifyNoInteractions(publisher);
    }

    @Test
    void guardar_contrato_listo_con_modulo_deshabilitado_no_publica_evento() {
        ContratoRepository contratos = mock(ContratoRepository.class);
        ClienteRepository clientes = mock(ClienteRepository.class);
        PuntoSuministroRepository puntos = mock(PuntoSuministroRepository.class);
        ContratoFileStorageService archivos = mock(ContratoFileStorageService.class);
        McasProposalService mcas = mock(McasProposalService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ContratoService service = new ContratoService(contratos, clientes, puntos, archivos, mcas, publisher);
        Cliente cliente = Cliente.builder().id(5).build();
        Contrato contrato = contratoListo(cliente);
        when(clientes.findById(5)).thenReturn(Optional.of(cliente));
        when(contratos.save(any())).thenAnswer(inv -> {
            Contrato guardado = inv.getArgument(0); guardado.setId(42); return guardado;
        });
        when(mcas.moduloHabilitado()).thenReturn(false);

        service.guardarContrato(contrato);

        verify(mcas).moduloHabilitado();
        verify(mcas, never()).triggerDesdeContrato(anyInt());
        verifyNoInteractions(publisher);
    }

    private Contrato contratoListo(Cliente cliente) {
        return Contrato.builder()
                .cliente(cliente)
                .contrato("C-2026-001")
                .fechaInicio(Date.valueOf(LocalDate.of(2026, 9, 1)))
                .idComercializadoraActual(7)
                .mcasTipoPrecio("FIJO")
                .mcasTipoSuministro("LUZ")
                .build();
    }
}
