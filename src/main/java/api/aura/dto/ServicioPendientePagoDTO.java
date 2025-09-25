package api.aura.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ServicioPendientePagoDTO {
    private Long idServicio;
    private String cliente;
    private String gestora;
    private String cups;
    private LocalDate fechaSiguientePago;
    private BigDecimal importeTotal;
    private Integer siguientePagoNumero;
    private BigDecimal importeCuotaPendiente;
    
    // Cuotas 1-6 (ya existentes)
    private Integer primerPagoPagado;
    private Integer segundoPagoPagado;
    private Integer tercerPagoPagado;
    private Integer cuartoPagoPagado;
    private Integer quintoPagoPagado;
    private Integer sextoPagoPagado;
    
    // Nuevas cuotas 7-12
    private Integer septimoPagoPagado;
    private Integer octavoPagoPagado;
    private Integer novenoPagoPagado;
    private Integer decimoPagoPagado;
    private Integer decimoprimerPagoPagado;
    private Integer decimosegundoPagoPagado;
    
    private String estadoPago;
    private Integer numeroCuotas;
}