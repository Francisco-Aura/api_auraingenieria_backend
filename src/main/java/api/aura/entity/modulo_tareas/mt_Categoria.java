package api.aura.entity.modulo_tareas;

import jakarta.persistence.*;
import java.util.Set;

@Entity
@Table(name = "modulotareas_categorias")
public class mt_Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column(nullable = false, unique = true, length = 5)
    private String codigo; // GE (gestion Energetica), FV (fotovolcaica), TM (telemedida), GENERALES

    @ManyToMany
    @JoinTable(
        name = "modulotareas_categorias_subcategorias",
        joinColumns = @JoinColumn(name = "categoria_id"),
        inverseJoinColumns = @JoinColumn(name = "subcategoria_id")
    )
    private Set<mt_SubCategoria> subcategorias;
}
