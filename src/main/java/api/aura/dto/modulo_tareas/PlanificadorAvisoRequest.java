package api.aura.dto.modulo_tareas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * Aviso que envía el planificador cuando alguien termina una tarea y otras quedan listas para empezar.
 * Se crea una notificación por cada usuario del grupo, salvo quien acaba de terminarla.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanificadorAvisoRequest {
    /** Clave del grupo destinatario: comercial, ingenieria, administracion, coordinacion. */
    private String group;
    private String groupName;
    /** Id del usuario que terminó la tarea (no se le avisa). Si no viene, se compara por nombre. */
    private Integer excludeUserId;
    private String excludeUser;
    /** Título que se muestra en la notificación (el proyecto). */
    private String title;
    private String message;
    /** Enlace al proyecto y la tarea ("proyecto=ID&tarea=ID"); se guarda tal cual y Vue lo abre al pulsar el aviso. */
    private String link;
    private String projectId;
    private String projectName;
    private String finishedTaskId;
    private String finishedTaskName;
    private String finishedBy;
}
