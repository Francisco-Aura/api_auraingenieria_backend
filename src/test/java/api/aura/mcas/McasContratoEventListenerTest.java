package api.aura.mcas;

import api.aura.service.mcas.McasContratoEventListener;
import api.aura.service.mcas.McasContratoGuardadoEvent;
import api.aura.service.mcas.McasProposalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El listener debe aislar el guardado del contrato de cualquier fallo de MCAS:
 * un error al generar la propuesta se registra pero NUNCA se propaga. Además
 * debe abrir una transacción NUEVA: en AFTER_COMMIT la del contrato ya está
 * confirmada y unirse a ella perdería las escrituras de la propuesta.
 */
class McasContratoEventListenerTest {

    private McasProposalService mcas;
    private PlatformTransactionManager transacciones;
    private McasContratoEventListener listener;

    @BeforeEach
    void setUp() {
        mcas = mock(McasProposalService.class);
        transacciones = mock(PlatformTransactionManager.class);
        when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        listener = new McasContratoEventListener(mcas, transacciones);
    }

    @Test
    void delega_en_el_servicio_dentro_de_una_transaccion_nueva() {
        listener.onContratoGuardado(new McasContratoGuardadoEvent(99));

        verify(mcas).triggerDesdeContrato(99);
        ArgumentCaptor<TransactionDefinition> definicion = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transacciones).getTransaction(definicion.capture());
        assertThat(definicion.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(transacciones).commit(any());
    }

    @Test
    void un_fallo_de_mcas_no_se_propaga_y_revierte_solo_la_transaccion_de_mcas() {
        when(mcas.triggerDesdeContrato(42)).thenThrow(new IllegalStateException("fallo MCAS"));

        assertThatCode(() -> listener.onContratoGuardado(new McasContratoGuardadoEvent(42)))
                .doesNotThrowAnyException();
        verify(mcas).triggerDesdeContrato(42);
        verify(transacciones).rollback(any());
        verify(transacciones, never()).commit(any());
    }

    @Test
    void un_fallo_al_confirmar_la_transaccion_de_mcas_tampoco_se_propaga() {
        doThrow(new TransactionSystemException("commit fallido")).when(transacciones).commit(any());

        assertThatCode(() -> listener.onContratoGuardado(new McasContratoGuardadoEvent(7)))
                .doesNotThrowAnyException();
        verify(mcas).triggerDesdeContrato(7);
    }
}
