package api.aura.dto.modulo_tareas;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TareaDTO {
    private Long id;
    private String titulo;
    private String descripcion;
    private Integer prioridad;
    private Boolean notificar;
    private Boolean enviarCorreo;
    private String repetir;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaVencimiento;
    private LocalDateTime fechaFinalizacion;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private Long estadoId;
    private String estadoNombre;
    private String estadoColor;
    private Boolean estadoFinal;
    private Boolean estadoBloqueo;
    private Integer usuarioAsignadoId;
    private String usuarioAsignadoNombre;
    private Integer usuarioCreadorId;
    private String usuarioCreadorNombre;
    private Integer nivelAccesoCreador;
    private Integer clienteRelacionadoId;
    private String clienteRelacionadoNombre;
    private String clientePotencialNombre;
    private Long categoriaId;
    private String categoriaNombre;
    private Long subcategoriaId;
    private String subcategoriaNombre;
    private Boolean vencida;
    private Boolean fijada;
    private List<ComentarioDTO> comentarios;
    private Long duracionActivaSegundos;
    private Long duracionTotalSegundos;
    private Long tiempoBloqueadoSegundos;
    private Integer totalBloqueos;
    private LocalDateTime fechaInicioProgreso;
    private String motivoBloqueo;
    private LocalDateTime fechaBloqueo;
    private Integer usuarioBloqueoId;
    private String usuarioBloqueoNombre;
}
