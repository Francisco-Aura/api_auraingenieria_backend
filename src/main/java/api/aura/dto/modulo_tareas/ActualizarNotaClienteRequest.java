package api.aura.dto.modulo_tareas;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActualizarNotaClienteRequest {

    @NotBlank(message = "El comentario es obligatorio")
    private String comentario;

    private String cups;
}
