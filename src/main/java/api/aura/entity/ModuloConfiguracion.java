package api.aura.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "configuracion_modulos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuloConfiguracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String codigo;

    @Column(nullable = false)
    private Boolean activo;

    @Column(length = 255)
    private String descripcion;
}
