package api.aura.entity.modulo_tareas;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "clientes_notas_adjuntos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteNotaAdjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_id", nullable = false)
    private ClienteNota nota;

    @Column(name = "nombre_archivo", length = 255, nullable = false)
    private String nombreArchivo;

    @Column(name = "ruta_archivo", length = 500, nullable = false)
    private String rutaArchivo;

    @Column(name = "tipo_mime", length = 100)
    private String tipoMime;

    @Column(name = "fecha_subida", nullable = false, updatable = false)
    private LocalDateTime fechaSubida;

    @PrePersist
    public void onCreate() {
        this.fechaSubida = LocalDateTime.now();
    }
}
