package api.aura.dto.mcas;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Comisión del contrato para quien lo graba
 * ({@code GET /api/mcas/propuestas/contrato/{idContrato}/resumen}). Sin traza,
 * códigos de regla, JSON ni usuarios: {@code mensaje} y {@code queFalta} ya van
 * redactados para el usuario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McasResumenContratoDTO {
    private boolean moduloActivo;
    private boolean hayPropuesta;
    private Long idPropuesta;
    private String estado;
    private BigDecimal importe;
    private Integer numeroPagos;
    /** Etiquetas de los datos que faltan («FEE del contrato»). */
    @Builder.Default
    private List<String> queFalta = List.of();
    private String mensaje;
    private LocalDateTime actualizadoEn;
    private boolean servicioCreado;
}
