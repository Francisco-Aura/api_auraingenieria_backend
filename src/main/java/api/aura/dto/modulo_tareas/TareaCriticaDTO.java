package api.aura.dto.modulo_tareas;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TareaCriticaDTO {
    // ID de la tarea
    private Long tareaId;

    // Título de la tarea
    private String titulo;

    // Tipo de criticidad: VENCIDA, PROXIMA_A_VENCER, BLOQUEADA, SIN_ASIGNAR
    private String tipoCriticidad;

    // Descripción del problema
    private String descripcion;

    // Nivel de urgencia (1-5, siendo 5 la más urgente)
    private Integer nivelUrgencia;

    // Usuario asignado
    private String usuarioAsignado;

    // Fecha de vencimiento
    private LocalDateTime fechaVencimiento;

    // Prioridad de la tarea (0-3)
    private Integer prioridad;
}
