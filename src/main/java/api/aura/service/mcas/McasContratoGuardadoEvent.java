package api.aura.service.mcas;

/**
 * Evento de dominio publicado por {@code ContratoService} cuando se guarda un
 * contrato comercialmente listo para comisionar. Lo consume
 * {@link McasContratoEventListener} tras el commit de la transacción de
 * guardado, de modo que la generación de la propuesta MCAS sea un efecto
 * secundario no bloqueante del alta/edición del contrato.
 */
public class McasContratoGuardadoEvent {

    private final Integer idContrato;

    public McasContratoGuardadoEvent(Integer idContrato) {
        this.idContrato = idContrato;
    }

    public Integer getIdContrato() {
        return idContrato;
    }
}
