package api.aura.dto.modulo_tareas;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadisticasTareasDTO {
    // Métricas básicas
    private long totalTareas;
    private long tareasPendientes;
    private long tareasEnProgreso;
    private long tareasCompletadas;
    private long tareasVencidas;
    private long tareasBloqueadas;

    // Métricas semanales
    private long tareasCompletadasSemana;
    private long totalTareasSemana;

    // Métricas de tiempo
    private long tiempoActivoTotalSegundos;
    private long tiempoActivoPromedioSegundos;
    private long tiempoBloqueadoTotalSegundos;

    // NUEVAS MÉTRICAS DE PRODUCTIVIDAD

    // Tasa de completación: (completadas / (pendientes + completadas)) * 100
    // Indica qué porcentaje de tareas asignadas se han completado
    private double tasaCompletacion;

    // Eficiencia: (completadas a tiempo / completadas) * 100
    // Indica qué porcentaje de tareas se completó antes de la fecha de vencimiento
    private double eficiencia;

    // Delay promedio en días para tareas vencidas
    private double delayPromedioDias;

    // Número de tareas bloqueadas en el período actual
    private long tareasBloqueadasPeriodo;

    // Número de tareas que vencen próximamente (próximos 3 días)
    private long tareasProximasAVencer;

    // Prioridad promedio de las tareas pendientes (0-3, siendo 0 urgente)
    private double prioridadPromedioPendientes;

    // Tareas sin asignar
    private long tareasSinAsignar;
}
