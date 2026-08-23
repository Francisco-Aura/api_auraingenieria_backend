package api.aura.dto.modulo_tareas;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteNotaAdjuntoDTO {
    private Long id;
    private String nombreArchivo;
    private String tipoMime;
}
