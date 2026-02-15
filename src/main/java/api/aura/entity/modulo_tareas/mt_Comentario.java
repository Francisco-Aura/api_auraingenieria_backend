package api.aura.entity.modulo_tareas;

import api.aura.entity.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "modulotareas_comentarios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class mt_Comentario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarea_id", nullable = false)
    private mt_Tarea tarea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuarioAutor;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String comentario;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(nullable = true)
    private LocalDateTime fechaEdicion;

    @Column(nullable = false)
    @Builder.Default
    private Boolean editado = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean fijado = false;

    @OneToMany(mappedBy = "comentario", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<mt_Adjunto> adjuntos = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
    }
}
