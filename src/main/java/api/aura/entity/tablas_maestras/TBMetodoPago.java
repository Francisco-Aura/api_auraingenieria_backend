package api.aura.entity.tablas_maestras;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "TBMetodoPago")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TBMetodoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 250)
    private String nombre;
}

