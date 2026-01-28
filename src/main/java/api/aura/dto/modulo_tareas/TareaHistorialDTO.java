package api.aura.dto.modulo_tareas;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TareaHistorialDTO {
    private Long id;
    private Long estadoId;
    private String estadoNombre;
    private String estadoColor;
    private Boolean estadoCuentaTiempo;
    private Boolean estadoBloqueo;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Long duracionActivaSegundos;
    private Long tiempoBloqueoSegundos;
    private String motivoBloqueo;
    private String comentario;
    private Integer usuarioId;
    private String usuarioNombre;
}
