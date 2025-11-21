package api.aura.entity.modulo_tareas;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "modulotareas_comentarios")
public class mt_Comentario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tarea_id", nullable = false)
    private mt_Tarea tarea;

    private Long idUsuarioAutor;

    @Column(columnDefinition = "TEXT")
    private String comentario;

    private LocalDateTime fechaComentario;

    // getters y setters
}
