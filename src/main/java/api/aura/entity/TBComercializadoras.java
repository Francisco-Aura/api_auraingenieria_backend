package api.aura.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "TBComercializadoras")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TBComercializadoras {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 250)
    private String nombre;

    @Column(length = 250)
    private String descripcion;
}
