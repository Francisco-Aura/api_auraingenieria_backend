package api.aura.service.modulo_registro_horario;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Festivos nacionales (fijos y Viernes Santo) más los extra de la configuración. Mismas reglas que el Registro Horario anterior. */
public final class CalendarioFestivos {

    /** (día, mes) de los festivos nacionales fijos. */
    private static final int[][] FIJOS = {{1, 1}, {6, 1}, {1, 5}, {15, 8}, {12, 10}, {1, 11}, {6, 12}, {8, 12}, {25, 12}};
    private static final Pattern FECHA_ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private CalendarioFestivos() {
    }

    /** Domingo de Pascua (algoritmo gregoriano). */
    public static LocalDate pascua(int anio) {
        int a = anio % 19, b = anio / 100, c = anio % 100;
        int d = b / 4, e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(anio, mes, dia);
    }

    /**
     * Festivos del año. Cada {@code extra} es «AAAA-MM-DD» (solo ese año) o «MM-DD» (todos los años); los que no son
     * una fecha válida se ignoran, como en el anterior.
     */
    public static Set<LocalDate> festivosDelAnio(int anio, Collection<String> extra) {
        Set<LocalDate> festivos = new TreeSet<>();
        for (int[] f : FIJOS) {
            festivos.add(LocalDate.of(anio, f[1], f[0]));
        }
        festivos.add(pascua(anio).minusDays(2)); // Viernes Santo
        for (String txt : extra) {
            LocalDate fecha = extra(anio, txt);
            if (fecha != null) {
                festivos.add(fecha);
            }
        }
        return festivos;
    }

    public static Set<LocalDate> festivosDelAnio(int anio) {
        return festivosDelAnio(anio, Set.of());
    }

    private static LocalDate extra(int anio, String txt) {
        if (txt == null) {
            return null;
        }
        try {
            if (txt.length() == 5) { // como el anterior: el carácter del medio no se mira
                return LocalDate.of(anio, entero(txt.substring(0, 2)), entero(txt.substring(3)));
            }
            return FECHA_ISO.matcher(txt).matches() ? LocalDate.parse(txt) : null;
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    private static int entero(String txt) {
        return Integer.parseInt(txt.strip());
    }
}
