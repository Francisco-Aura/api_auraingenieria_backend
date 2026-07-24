package api.aura.entity.modulo_tareas;

import api.aura.entity.Cliente;
import api.aura.entity.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "clientes_notas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteNota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuarioAutor;

    @Column(length = 50)
    private String cups;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String comentario;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_edicion")
    private LocalDateTime fechaEdicion;

    @Column(name = "editado", nullable = false)
    @Builder.Default
    private Boolean editado = false;

    @Column(name = "fijado", nullable = false)
    @Builder.Default
    private Boolean fijado = false;

    @Column(name = "nombre_archivo", length = 255)
    private String nombreArchivo;

    @Column(name = "ruta_archivo", length = 500)
    private String rutaArchivo;

    @Column(name = "tipo_mime", length = 100)
    private String tipoMime;

    @PrePersist
    public void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.editado == null) {
            this.editado = false;
        }
        if (this.fijado == null) {
            this.fijado = false;
        }
    }
}
