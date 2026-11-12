package api.aura.service.mcas;

/**
 * Algo ha cambiado en los contratos de un punto de suministro (baja del contrato, del punto o
 * del cliente, renovación o contrato nuevo): hay que mirar si toca retrocesión.
 */
public record McasBajaEvent(Integer idPuntoSuministro) {}
