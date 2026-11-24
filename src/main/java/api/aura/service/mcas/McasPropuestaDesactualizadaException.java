package api.aura.service.mcas;

/** La propuesta ya no representa los datos actuales del contrato. */
public class McasPropuestaDesactualizadaException extends IllegalStateException {
    public McasPropuestaDesactualizadaException(String message) {
        super(message);
    }
}
