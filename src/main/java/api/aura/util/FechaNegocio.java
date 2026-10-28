package api.aura.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * Conversión de {@link Date} a {@link LocalDate} sin depender de la zona horaria
 * de la JVM.
 *
 * <p>Jackson (JacksonConfig) interpreta las fechas del JSON ("2026-03-16") como la
 * medianoche de Europe/Madrid. Convertirlas con la zona por defecto de la JVM
 * ({@code new java.sql.Date(d.getTime()).toLocalDate()}) las adelanta un día si la
 * JVM no corre en Europe/Madrid (p. ej. UTC: 2026-03-15T23:00Z → 15/03).</p>
 *
 * <p>Las fechas leídas de BD por Hibernate ya son {@link java.sql.Date} y se
 * convierten tal cual, igual que antes. Con la JVM en Europe/Madrid el resultado
 * es idéntico al de la conversión anterior.</p>
 */
public final class FechaNegocio {

    /** Zona de negocio: la misma que usa Jackson para (de)serializar fechas. */
    public static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private FechaNegocio() {}

    public static LocalDate aLocalDate(Date valor) {
        if (valor == null) return null;
        if (valor instanceof java.sql.Date sql) return sql.toLocalDate();
        return valor.toInstant().atZone(ZONA).toLocalDate();
    }
}
