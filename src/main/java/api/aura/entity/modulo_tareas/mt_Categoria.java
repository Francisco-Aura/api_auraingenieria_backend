package api.aura.entity.modulo_tareas;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "modulotareas_categorias")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class mt_Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true, length = 10)
    private String codigo;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    private Boolean activo;

    @ManyToMany
    @JoinTable(name = "modulotareas_categorias_subcategorias", joinColumns = @JoinColumn(name = "categoria_id"), inverseJoinColumns = @JoinColumn(name = "subcategoria_id"))
    @Builder.Default
    private List<mt_SubCategoria> subcategorias = new ArrayList<>();
}
