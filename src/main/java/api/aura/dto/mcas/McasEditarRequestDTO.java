package api.aura.dto.mcas;
import lombok.Data;
import java.math.BigDecimal;

@Data public class McasEditarRequestDTO {
    private BigDecimal importeDefinitivo;
    private Integer numeroPagos;
    private String overrideJson;
    private String motivo;
}
