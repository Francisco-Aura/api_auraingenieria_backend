package api.aura.entity.tablas_maestras;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "tb_propiedades")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TBPropiedad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 255, nullable = false, unique = true)
    private String nombre;
} 