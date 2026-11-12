package api.aura.dto.mcas;
import lombok.Data;
import java.math.BigDecimal;

@Data public class McasLineaDTO {
    private Long id;
    private String tipoLinea, descripcion, metodo;
    private BigDecimal valorBase, valorTasa, valorPorcentaje;
    /** importeAutomatico = importe_calculado en DB */
    private BigDecimal importeAutomatico;
    private BigDecimal importeDefinitivo;
    private Integer idRegla, orden;
}
