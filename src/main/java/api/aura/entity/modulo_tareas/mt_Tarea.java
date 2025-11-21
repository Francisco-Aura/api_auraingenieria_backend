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

    private Long idUsuarioAsignado; 
    private Long idUsuarioCreador;
    private Long idCliente;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFinalizacion;
    private LocalDateTime fechaVencimiento;    
    
    @Enumerated(EnumType.STRING)
    private Prioridad prioridad = Prioridad.MEDIA;

    @Enumerated(EnumType.STRING)
    private EstadoTarea estado = EstadoTarea.PENDIENTE;

    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private mt_Categoria categoria;

    @ManyToOne
    @JoinColumn(name = "subcategoria_id")
    private mt_SubCategoria subcategoria;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    // getters y setters

    public enum EstadoTarea {        
        PENDIENTE,
        EN_PROGRESO,
        BLOQUEADA,
        COMPLETADA,
        CANCELADA
    }

    public enum Prioridad {
        BAJA,
        MEDIA,
        ALTA,
        URGENTE
    }
}
