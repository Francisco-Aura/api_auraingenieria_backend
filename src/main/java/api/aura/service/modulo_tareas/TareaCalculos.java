package api.aura.service.modulo_tareas;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Utilidades de cálculo de tiempo y prioridad para tareas.
 * Métodos estáticos sin estado — seguros para uso concurrente.
 */
final class TareaCalculos {

    private TareaCalculos() {}

    static long segundosEntre(LocalDateTime inicio, LocalDateTime fin) {
        if (inicio == null || fin == null) {
            return 0L;
        }
        return Math.max(Duration.between(inicio, fin).getSeconds(), 0);
    }

    static boolean sonFechasIguales(LocalDateTime fecha1, LocalDateTime fecha2) {
        if (fecha1 == null && fecha2 == null) return true;
        if (fecha1 == null || fecha2 == null) return false;
        long diferenciaMinutos = ChronoUnit.MINUTES.between(fecha2, fecha1);
        return Math.abs(diferenciaMinutos) <= 1;
    }

    static String obtenerNombrePrioridad(Integer prioridad) {
        if (prioridad == null) return "Media";
        return switch (prioridad) {
            case 0 -> "Urgente";
            case 1 -> "Alta";
            case 2 -> "Media";
            case 3 -> "Baja";
            default -> "Media";
        };
    }
}
