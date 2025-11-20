package api.aura.entity.modulo_tareas;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "modulotareas_tareas")
public class mt_Tarea {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    private Long assignedTo; 
    private Long createdBy;
    private Long clientId;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime dueDate;

    private Boolean important = false;

    @Enumerated(EnumType.STRING)
    private EstadoTarea status = EstadoTarea.PENDING;

    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private mt_Categoria categoria;

    @ManyToOne
    @JoinColumn(name = "subcategoria_id")
    private mt_SubCategoria subcategoria;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // getters y setters

    public enum EstadoTarea {
        PENDING,
        IN_PROGRESS,
        COMPLETED
    }
}
