package api.aura.dto.modulo_tareas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActualizarNotaClienteRequest {

    @NotBlank(message = "El comentario es obligatorio")
    @Size(max = 2000, message = "El comentario no puede exceder 2000 caracteres")
    private String comentario;

    private String cups;
}
