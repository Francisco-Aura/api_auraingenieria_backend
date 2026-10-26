package api.aura.entity.modulo_tareas;

import api.aura.entity.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "modulotareas_notificaciones")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class mt_Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tarea del módulo de tareas. Es null en las notificaciones que vienen de otros sistemas (el planificador). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarea_id")
    private mt_Tarea tarea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuarioDestino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNotificacion tipo;

    @Column(nullable = false, length = 255)
    private String mensaje;

    @Column(nullable = false)
    private Boolean leida;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    /** Sistema del que viene la notificación cuando no es una tarea del módulo (p. ej. "PLANIFICADOR"). */
    @Column(length = 30)
    private String origen;

    /** Título que se muestra cuando no hay tarea (p. ej. el nombre del proyecto del planificador). */
    @Column(length = 255)
    private String titulo;

    /** A dónde lleva al pulsarla, cuando viene de otro sistema (planificador: "proyecto=ID&tarea=ID"). */
    @Column(length = 255)
    private String enlace;

    @PrePersist
    public void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (leida == null) {
            this.leida = Boolean.FALSE;
        }
    }

    public enum TipoNotificacion {
        ASIGNADA,
        CAMBIO_ESTADO,
        VENCIMIENTO,
        COMENTARIO,
        COMPLETADA,
        MENCION,
        PLANIFICADOR
    }
}
