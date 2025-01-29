package api.aura.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.Date;

@Data
public class ClienteDTO {
    private Integer id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 250, message = "El nombre no puede superar los 250 caracteres")
    private String nombre;

    @NotBlank(message = "El CIF es obligatorio")
    @Size(max = 50, message = "El CIF no puede superar los 50 caracteres")
    private String cif;

    @Size(max = 200)
    private String direccion;

    @Size(max = 100)
    private String poblacion;

    @Size(max = 100)
    private String provincia;

    @Size(max = 10)
    private String codigoPostal;

    private String nombreAutorizado;
    private String telefonoAutorizado;
    private String dniAutorizado;
    private String emailAutorizado;
    private String nombreContacto1;
    private String cargoContacto1;
    private String telefonoContacto1;
    private String emailContacto1;
    private String nombreContacto2;
    private String cargoContacto2;
    private String telefonoContacto2;
    private String emailContacto2;
    private String nombreContacto3;
    private String cargoContacto3;
    private String telefonoContacto3;
    private String emailContacto3;

    @Size(max = 100, message = "El IBAN no puede superar los 100 caracteres")
    private String iban;

    private String notas;
    private Date fechaAlta;
    private Date fechaBaja;
    private String nombreComercial;
    private Integer codigoSector;
    private Integer codigoSubtipologia;
    private String actividad;
    private String userTelemedida;
    private String passTelemedida;

    private Integer idUsuario;
}
