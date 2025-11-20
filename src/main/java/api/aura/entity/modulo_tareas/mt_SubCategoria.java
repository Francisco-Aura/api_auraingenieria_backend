package api.aura.entity.modulo_tareas;

import jakarta.persistence.*;
import java.util.Set;

@Entity
@Table(name = "modulotareas_subcategorias")
public class mt_SubCategoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @ManyToMany(mappedBy = "subcategorias")
    private Set<mt_Categoria> categorias;

    // getters y setters
}
