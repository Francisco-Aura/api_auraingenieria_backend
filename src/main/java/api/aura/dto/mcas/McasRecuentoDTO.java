package api.aura.dto.mcas;

/**
 * Propuestas pendientes para el contador del menú
 * ({@code GET /api/mcas/propuestas/recuento}).
 *
 * @param porValidar   PENDIENTE_VALIDACION
 * @param porRevisar   REQUIERE_DATOS, REQUIERE_REVISION_MANUAL, ERROR_CALCULO, PENDIENTE_CALCULO y las
 *                     DESACTUALIZADO que siguen siendo la última propuesta de su contrato
 * @param enCorreccion RECHAZADO y EN_EDICION
 * @param total        suma de las tres
 */
public record McasRecuentoDTO(long porValidar, long porRevisar, long enCorreccion, long total) {
}
