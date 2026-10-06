package api.aura.service.mcas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dispara la generación de la propuesta MCAS DESPUÉS de que la transacción que
 * guarda el contrato haya hecho commit.
 *
 * <p>Motivos de ejecutarlo tras el commit y no dentro de la transacción de
 * guardado:</p>
 * <ul>
 *   <li><b>Aislamiento:</b> cualquier fallo calculando la comisión se registra
 *   pero nunca revierte el guardado del contrato (MCAS es un efecto secundario,
 *   no una precondición del alta).</li>
 *   <li><b>Sin interbloqueos:</b> {@code triggerDesdeContrato} hace
 *   {@code SELECT ... FOR UPDATE} sobre la fila del contrato; ejecutarlo tras el
 *   commit garantiza que la fila ya está confirmada y disponible.</li>
 * </ul>
 *
 * <p>En la fase AFTER_COMMIT los recursos de la transacción original siguen
 * enlazados al hilo: un método {@code @Transactional} con propagación REQUIRED
 * se uniría a esa transacción ya confirmada y sus escrituras nunca llegarían a
 * la base de datos (o fallaría el bloqueo por falta de transacción activa). Por
 * eso el cálculo se ejecuta explícitamente en una transacción NUEVA
 * ({@code REQUIRES_NEW}) mediante un {@link TransactionTemplate}, dentro del
 * try/catch, para que ni siquiera un fallo al confirmarla se propague al
 * guardado del contrato.</p>
 */
@Component
public class McasContratoEventListener {

    private static final Logger log = LoggerFactory.getLogger(McasContratoEventListener.class);

    private final McasProposalService mcasProposalService;
    private final TransactionTemplate transaccionNueva;

    public McasContratoEventListener(McasProposalService mcasProposalService,
                                     PlatformTransactionManager transactionManager) {
        this.mcasProposalService = mcasProposalService;
        this.transaccionNueva = new TransactionTemplate(transactionManager);
        this.transaccionNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContratoGuardado(McasContratoGuardadoEvent event) {
        try {
            transaccionNueva.executeWithoutResult(estado ->
                    mcasProposalService.triggerDesdeContrato(event.getIdContrato()));
        } catch (Exception e) {
            log.error("MCAS: fallo generando la propuesta del contrato {} "
                    + "(el contrato se guardó correctamente; revisar manualmente)",
                    event.getIdContrato(), e);
        }
    }
}
