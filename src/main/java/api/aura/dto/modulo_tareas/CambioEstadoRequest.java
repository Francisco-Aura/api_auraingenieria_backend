package api.aura.dto.modulo_tareas;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambioEstadoRequest {
    private Long estadoId;
    private String motivoBloqueo;
    private String comentario;
    private Integer usuarioAccionId;
}
