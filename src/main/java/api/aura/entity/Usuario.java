package api.aura.entity;

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

    @Column(length = 100)
    private String user;

    @Column(length = 100)
    private String pass;

    private Integer nivelAcceso;
}
