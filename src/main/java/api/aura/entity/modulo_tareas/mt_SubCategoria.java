package api.aura.entity.modulo_tareas;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "modulotareas_subcategorias")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class mt_SubCategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(length = 10)
    private String codigo;

    @Column(nullable = false)
    private Boolean activo;

    @ManyToMany(mappedBy = "subcategorias")
    @Builder.Default
    private List<mt_Categoria> categorias = new ArrayList<>();
}
