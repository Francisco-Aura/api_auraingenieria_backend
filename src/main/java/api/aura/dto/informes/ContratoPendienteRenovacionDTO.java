package api.aura.dto.informes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.sql.Date;

@Data
public class ContratoPendienteRenovacionDTO {
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
    // No se serializan al frontend: este informe no las muestra ni las usa,
    // y viajarían credenciales de telemedida en claro en la respuesta.
    @JsonIgnore
    private String userTelemedida;
    @JsonIgnore
    private String passTelemedida;
    private String contrato;
    private Integer idTarifaLuz;
    private String tarifaLuz;
    private Integer idTarifaGas;
    private String tarifaGas;
    private Double  pot1;
    private Double  pot2;
    private Double  pot3;
    private Double  pot4;
    private Double  pot5;
    private Double pot6;
    private Double  consumoAnual;
    private Integer exencionIE;
    private Integer autoconsumo;
    private Integer idComercializadoraActual;
    private String comercializadoraActual;
    private Integer idComercializadoraAnterior;
    private String comercializadoraAnterior;
    private String observaciones;
    private String comentarioRenovacion;
    private String usuarioAsignado;
}
