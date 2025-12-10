package api.aura.dto.modulo_tareas;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComentarioDTO {
    private Long id;
    private Long tareaId;
    private Integer usuarioId;
    private String usuarioNombre;
    private String comentario;
    private LocalDateTime fechaCreacion;
}
