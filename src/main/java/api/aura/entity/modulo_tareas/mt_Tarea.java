package api.aura.entity.modulo_tareas;

import api.aura.entity.Cliente;
import api.aura.entity.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "modulotareas_tareas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class mt_Tarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private Integer prioridad;

    @Column(nullable = false)
    private Boolean notificar;

    @Column(nullable = false)
    private Boolean enviarCorreo;

    @Column(length = 20)
    private String repetir;

    private LocalDateTime fechaInicio;

    private LocalDateTime fechaVencimiento;

    private LocalDateTime fechaFinalizacion;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaActualizacion;

    @Column(nullable = false)
    @Builder.Default
    private Long duracionActivaSegundos = 0L;

    private LocalDateTime fechaInicioProgreso;

    @Column(nullable = false)
    @Builder.Default
    private Long tiempoBloqueadoSegundos = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalBloqueos = 0;

    @Column(length = 255)
    private String motivoBloqueo;

    private LocalDateTime fechaBloqueo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean fijada = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean notificadoProximoVencimiento = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_bloqueo_id")
    private Usuario usuarioBloqueo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_asignado_id")
    private Usuario usuarioAsignado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_creador_id")
    private Usuario usuarioCreador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente clienteRelacionado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_id")
    private mt_TareaEstado estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private mt_Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategoria_id")
    private mt_SubCategoria subcategoria;

    @OneToMany(mappedBy = "tarea", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<mt_Comentario> comentarios = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
        if (prioridad == null) {
            this.prioridad = 2;
        }
        if (notificar == null) {
            this.notificar = Boolean.FALSE;
        }
        if (enviarCorreo == null) {
            this.enviarCorreo = Boolean.FALSE;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
