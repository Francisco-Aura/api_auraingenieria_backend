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

    /**
     * Misma fecha y hora al minuto (se ignoran segundos y milisegundos). Antes valía un
     * margen de casi dos minutos y mover el vencimiento de 18:00 a 18:01 no contaba como
     * cambio: ni avisaba al asignado ni rearmaba el aviso de «vence en 1 hora».
     */
    static boolean sonFechasIguales(LocalDateTime fecha1, LocalDateTime fecha2) {
        if (fecha1 == null && fecha2 == null) return true;
        if (fecha1 == null || fecha2 == null) return false;
        return fecha1.truncatedTo(ChronoUnit.MINUTES).equals(fecha2.truncatedTo(ChronoUnit.MINUTES));
    }

    /** true si {@code fecha} cae dentro del intervalo, con los dos extremos incluidos. */
    static boolean dentroDe(LocalDateTime fecha, LocalDateTime desde, LocalDateTime hasta) {
        return fecha != null && !fecha.isBefore(desde) && !fecha.isAfter(hasta);
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
