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
 * Revisa las retrocesiones de un punto DESPUÉS de que se confirme el guardado que las puede
 * provocar (baja, renovación, contrato nuevo), en una transacción nueva: si falla, se anota y
 * el guardado del contrato, del punto o del cliente no se entera (igual que
 * {@link McasContratoEventListener}).
 */
@Component
public class McasBajaEventListener {

    private static final Logger log = LoggerFactory.getLogger(McasBajaEventListener.class);

    private final McasRetrocesionService retrocesiones;
    private final TransactionTemplate transaccionNueva;

    public McasBajaEventListener(McasRetrocesionService retrocesiones, PlatformTransactionManager transactionManager) {
        this.retrocesiones = retrocesiones;
        this.transaccionNueva = new TransactionTemplate(transactionManager);
        this.transaccionNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCambioEnElPunto(McasBajaEvent event) {
        try {
            transaccionNueva.executeWithoutResult(estado -> retrocesiones.revisarPunto(event.idPuntoSuministro()));
        } catch (Exception e) {
            log.error("MCAS: fallo revisando las retrocesiones del punto {} (lo guardado no se ve afectado)",
                    event.idPuntoSuministro(), e);
        }
    }
}
