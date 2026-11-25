package api.aura.entity.modulo_tareas;

import api.aura.entity.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Periodo en el que un usuario trabaja en una tarea. Sin {@code fin} es el que está corriendo. */
@Entity
@Table(name = "modulotareas_tramos_tiempo")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class mt_TareaTramo {

    /** Motivos de cierre. */
    public static final String CIERRE_MANUAL = "MANUAL";
    public static final String CIERRE_CAMBIO_TAREA = "CAMBIO_TAREA";
    public static final String CIERRE_COMPLETADA = "COMPLETADA";
    public static final String CIERRE_CADUCADO = "CADUCADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tarea_id", nullable = false)
    private mt_Tarea tarea;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime inicio;

    private LocalDateTime fin;

    @Column(length = 20)
    private String cierre;
}
