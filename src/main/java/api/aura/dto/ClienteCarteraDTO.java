package api.aura.dto;

import lombok.Data;

@Data
public class ClienteCarteraDTO {

    private Integer idCliente;
    private String nombre;
    private String cif;
    private String direccionFiscal;
    private Long numeroPuntosSuministro;
    private String empleadoAsignado;

    // Constructor requerido por la consulta JPQL
    public ClienteCarteraDTO(Integer idCliente, String nombre, String cif, String direccionFiscal, Long numeroPuntosSuministro, String empleadoAsignado) {
        this.idCliente = idCliente;
        this.nombre = nombre;
        this.cif = cif;
        this.direccionFiscal = direccionFiscal;
        this.numeroPuntosSuministro = numeroPuntosSuministro;
        this.empleadoAsignado = empleadoAsignado;
    }
}
