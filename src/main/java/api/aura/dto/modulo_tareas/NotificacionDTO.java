package api.aura.dto.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_Notificacion;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionDTO {
    private Long id;
    private Long tareaId;
    private String tareaTitulo;
    private mt_Notificacion.TipoNotificacion tipo;
    private String mensaje;
    private Boolean leida;
    private LocalDateTime fechaCreacion;
}
