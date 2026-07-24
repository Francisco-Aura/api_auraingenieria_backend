package api.aura.dto.modulo_tareas;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteNotaDTO {
    private Long comentarioId;
    private Long tareaId;
    private String tituloTarea;
    private String cups;
    private String comentario;
    private String usuarioNombre;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaEdicion;
    private Boolean editado;
    private Boolean fijado;
    private String nombreArchivo;
    private String tipoMime;
}
