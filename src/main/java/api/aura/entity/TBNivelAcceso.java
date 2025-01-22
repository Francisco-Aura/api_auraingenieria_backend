package api.aura.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "TBNivelAcceso")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TBNivelAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 250)
    private String nivel;

    @Column(length = 250)
    private String descripcion;
}
