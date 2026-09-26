package api.aura.dto.informes;

import lombok.Data;
import java.sql.Date;
import java.math.BigDecimal;

@Data
public class ServicioVigenteDTO {
    private Long idServicio;
    private String cliente;
    private String servicio;
    private Date fechaInicioServicio;
    private Date fechaFinServicio;
    private Date fechaPago;
    private String gestora;
    private String producto;
    private String comercializadora;
    private String cups;
    private BigDecimal importe;
    private BigDecimal descuento;
    private BigDecimal importeFinal;
    private String metodoPago;
    private String pagoPlazos;
    private Integer numeroCuotas;
    private String observaciones;
    private String estadoVencimiento;
    private String estadoPago;
    private String tarifa;
    private String grupo;
}
