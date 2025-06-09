package api.aura.dto;

import lombok.Data;

@Data
public class ClienteCarteraDTO {

    private Integer idCliente;
    private String nombre;
    private String cif;
    private String direccionFiscal;
    private Long numeroPuntosSuministro;
    private String usuarioAsignado;
    private String nombreComercial;

    // Constructor requerido por la consulta JPQL
    public ClienteCarteraDTO(Integer idCliente, String nombre, String cif, String direccionFiscal, Long numeroPuntosSuministro, String usuarioAsignado, String nombreComercial) {
        this.idCliente = idCliente;
        this.nombre = nombre;
        this.cif = cif;
        this.direccionFiscal = direccionFiscal;
        this.numeroPuntosSuministro = numeroPuntosSuministro;
        this.usuarioAsignado = usuarioAsignado;
        this.nombreComercial = nombreComercial;
    }
}
