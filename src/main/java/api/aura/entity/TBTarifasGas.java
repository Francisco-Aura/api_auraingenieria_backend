package api.aura.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "TBTarifasGas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TBTarifasGas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 250)
    private String nombre;

    @Column(length = 100)
    private String descripcion;
}
