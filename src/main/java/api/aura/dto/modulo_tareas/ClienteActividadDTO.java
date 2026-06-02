package api.aura.dto.modulo_tareas;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteActividadDTO {
    private Long id;
    private String tipoEvento;
    private String descripcion;
    private String origen;
    private String cups;
    private Boolean soloNivelUno;
    private LocalDateTime fechaEvento;
}
