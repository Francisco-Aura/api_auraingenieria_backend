package api.aura.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 250)
    private String nombre;

    @Column(length = 200)
    private String email;

    @Column(length = 10)
    private String telefono;

    /**
     * Usuario del login. Solo se recibe: la pantalla de Administración lo lee de la
     * vista de administrador (UsuarioController); en el resto de respuestas (listado
     * de usuarios para cualquier usuario, usuario asignado de cada cliente...) no se
     * enseña, para no dar a todos la mitad de las credenciales de los demás.
     */
    @Column(length = 100)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String user;

    /**
     * Contraseña cifrada (AES-GCM, prefijo «{c}»; ver CifradoContrasenas) o, si quedara
     * alguna de antes, hash BCrypt. Solo se recibe (alta/cambio), nunca se devuelve ni se
     * escribe en logs.
     */
    @Column(length = 100)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String pass;

    private Integer nivelAcceso;
}
