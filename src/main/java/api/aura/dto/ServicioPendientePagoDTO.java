package api.aura.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ServicioPendientePagoDTO {
    private Long idServicio;
    private String cliente;
    private String servicio;
    private LocalDate fechaVencimiento;
    private BigDecimal importeTotal;
    private Integer primerPagoPagado;
    private Integer segundoPagoPagado;
    private Integer tercerPagoPagado;
    private Integer cuartoPagoPagado;
    private Integer quintoPagoPagado;
    private Integer sextoPagoPagado;
    private String estadoVencimiento;
    private String estadoPago;
} 