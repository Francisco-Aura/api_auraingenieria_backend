package api.aura.dto.informes;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ContratoPendienteRenovacionDTO {
    private Integer codigoContrato;
    private LocalDate fecha;
    private LocalDate fechaEnvio;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String idCliente;
    private String cliente;
    private String idPuntoSuministro;
    private String cupsLuz;
    private String cupsGas;
    private String userTelemedida;
    private String passTelemedida;
    private String contrato;
    private String idTarifaLuz;
    private String tarifaLuz;
    private String idTarifaGas;
    private String tarifaGas;
    private String pot1;
    private String pot2;
    private String pot3;
    private String pot4;
    private String pot5;
    private String pot6;
    private String consumoAnual;
    private String exencionIE;
    private String autoconsumo;
    private String idComercializadoraActual;
    private String comercializadoraActual;
    private String idComercializadoraAnterior;
    private String comercializadoraAnterior;
    private String observaciones;
}
