package api.aura.dto.informes;

import lombok.Data;
import java.sql.Date;

@Data
public class ContratoVigenteDTO {
    private Integer codigoContrato;
    private Date fecha;
    private Date fechaEnvio;
    private Date fechaInicio;
    private Date fechaFin;
    private Integer idCliente;
    private String cliente;
    private Integer idPuntoSuministro;
    private String cupsLuz;
    private String cupsGas;
    private String contrato;
    private Integer idTarifaLuz;
    private String tarifaLuz;
    private Integer idTarifaGas;
    private String tarifaGas;
    private Double potencia1;
    private Double potencia2;
    private Double potencia3;
    private Double potencia4;
    private Double potencia5;
    private Double potencia6;
    private Double consumoAnual;
    private Integer exencionIE;
    private Integer autoconsumo;
    private Integer idComercializadoraActual;
    private String comercializadoraActual;
    private Integer idComercializadoraAnterior;
    private String comercializadoraAnterior;
    private String observaciones;
}
