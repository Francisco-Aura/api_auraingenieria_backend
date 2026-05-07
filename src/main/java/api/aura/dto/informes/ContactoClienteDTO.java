package api.aura.dto.informes;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactoClienteDTO {
    private String nombreCliente;
    private String tipoContacto;
    private String nombre;
    private String cargo;
    private String telefono;
    private String correo;
    private String sector;
    private String estadoBaja;
}
