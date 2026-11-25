package api.aura.dto.modulo_tareas;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** Respuestas del control de tiempo. */
public final class TiempoDTO {

    private TiempoDTO() {
    }

    /** Estado del cronómetro del usuario. {@code activo}: está corriendo; si no, {@code ultimaTareaId} es la que reanudaría. */
    @Getter
    @AllArgsConstructor
    public static class Estado {
        private final boolean activo;
        private final Long tareaId;
        private final String tareaTitulo;
        private final LocalDateTime inicio;
        /** Segundos del tramo en curso (0 si está en pausa). */
        private final long segundosTramo;
        /** Segundos que lleva el usuario hoy en esa tarea, incluido el tramo en curso. */
        private final long segundosHoyTarea;
        /** Tarea que se reanudaría; null si no hay ninguna anterior o si ya está corriendo. */
        private final Long ultimaTareaId;
        private final String ultimaTareaTitulo;
    }

    @Getter
    @AllArgsConstructor
    public static class PorTarea {
        private final Long tareaId;
        private final String titulo;
        private final long segundos;
    }

    @Getter
    @AllArgsConstructor
    public static class PorDia {
        private final java.time.LocalDate fecha;
        private final long segundos;
    }

    @Getter
    @AllArgsConstructor
    public static class Resumen {
        private final Integer usuarioId;
        private final LocalDateTime desde;
        private final LocalDateTime hasta;
        private final long segundosTotales;
        private final List<PorTarea> tareas;
        /** Segundos por día (solo los días con tiempo), de más antiguo a más reciente. */
        private final List<PorDia> dias;
    }
}
