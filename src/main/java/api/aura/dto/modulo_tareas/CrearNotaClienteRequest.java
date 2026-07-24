package api.aura.dto.modulo_tareas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrearNotaClienteRequest {

    @NotNull(message = "El usuarioId es obligatorio")
    private Integer usuarioId;

    @NotBlank(message = "El comentario es obligatorio")
    @Size(max = 2000, message = "El comentario no puede exceder 2000 caracteres")
    private String comentario;

    private String cups;

    private Boolean fijado;
}
