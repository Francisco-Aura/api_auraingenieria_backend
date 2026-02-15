package api.aura.dto.modulo_tareas;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private LocalDateTime fechaEdicion;

    @Builder.Default
    private Boolean editado = false;

    @Builder.Default
    private Boolean fijado = false;

    @Builder.Default
    private List<AdjuntoDTO> adjuntos = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdjuntoDTO {
        private Long id;
        private String nombreArchivo;
        private Long tamanio;
        private String tipoMime;
        private LocalDateTime fechaSubida;
    }
}
