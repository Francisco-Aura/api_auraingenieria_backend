package api.aura.entity.tablas_maestras;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "TBSectores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TBSectores {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 250)
    private String nombre;

    @Column(length = 100)
    private String descripcion;
}
