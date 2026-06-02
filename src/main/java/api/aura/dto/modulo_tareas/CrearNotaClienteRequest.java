package api.aura.dto.modulo_tareas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrearNotaClienteRequest {

    @NotNull(message = "El usuarioId es obligatorio")
    private Integer usuarioId;

    @NotBlank(message = "El comentario es obligatorio")
    private String comentario;

    private String cups;
}
