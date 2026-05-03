package api.aura.entity.modulo_tareas;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "modulotareas_adjuntos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class mt_Adjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String nombreArchivo;

    @Column(nullable = false, length = 500)
    private String rutaArchivo;

    @Column(length = 100)
    private String tipoMime;

    @Column(nullable = false)
    private Long tamanio; // en bytes

    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaSubida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comentario_id", nullable = true)
    private mt_Comentario comentario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarea_id", nullable = true)
    private mt_Tarea tarea;

    @PrePersist
    public void onCreate() {
        this.fechaSubida = LocalDateTime.now();
    }
}
