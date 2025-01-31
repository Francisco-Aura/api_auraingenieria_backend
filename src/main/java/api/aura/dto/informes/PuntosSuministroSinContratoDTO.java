package api.aura.dto.informes;

import lombok.Data;

@Data
public class PuntosSuministroSinContratoDTO {
    
    private String idPS;
    private String idCliente;
    private String cliente;
    private String cupsLuz;
    private String cupsGas;
    private String direccion;
    private String codigoPostal;
    private String poblacion;
    private String provincia;

}
