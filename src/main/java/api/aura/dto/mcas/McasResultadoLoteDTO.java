package api.aura.dto.mcas;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado de un elemento en una operación por lotes (aprobar propuestas,
 * activar tablas de comisiones): cada uno se procesa aparte y el lote devuelve
 * resultados parciales.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McasResultadoLoteDTO {
    private Long id;
    private boolean ok;
    /** Estado en que queda el elemento (el que tenía si falló). */
    private String estado;
    private String mensaje;

    public static McasResultadoLoteDTO ok(Long id, String estado, String mensaje) {
        return new McasResultadoLoteDTO(id, true, estado, mensaje);
    }

    public static McasResultadoLoteDTO error(Long id, String estado, String mensaje) {
        return new McasResultadoLoteDTO(id, false, estado, mensaje);
    }
}
