package api.aura.dto.mcas;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Contrato encontrado por número o id para «Calcular propuesta»
 * ({@code GET /api/mcas/propuestas/buscar-contrato?ref=}): lo justo para elegir
 * el correcto de la lista.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McasContratoEncontradoDTO {
    private Integer id;
    private String numero;
    private String cliente;
    /** CUPS de luz o, si no hay, el de gas */
    private String cups;
    private String comercializadora;
    private LocalDate fechaInicio;
}
