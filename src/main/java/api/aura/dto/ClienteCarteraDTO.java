package api.aura.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ClienteCarteraDTO {
    private Integer idCliente;
    private String nombre;
    private String cif;
    private String direccionFiscal;
    private Integer numeroPuntosSuministro;
    private String usuarioAsignado;
}
