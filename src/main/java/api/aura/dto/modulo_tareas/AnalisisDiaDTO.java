package api.aura.dto.modulo_tareas;

import lombok.*;
import java.time.DayOfWeek;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalisisDiaDTO {
    // Día de la semana (MONDAY, TUESDAY, etc)
    private DayOfWeek dia;
    
    // Nombre del día en español
    private String nombreDia;
    
    // Número de tareas completadas en este día
    private long completadasEseDia;
    
    // Número total de tareas asignadas para este día
    private long totalAsignadasEseDia;
    
    // Tasa de productividad ese día
    private double tasaProductividad;
}
