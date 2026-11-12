package api.aura.dto.mcas;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data public class McasCalendarioDTO {
    private Long id;
    private Integer numeroPago;
    private LocalDate fechaPago;
    private BigDecimal importe;
    private String estado, naturaleza;
}
