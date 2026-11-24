package api.aura.service.modulo_planificador.estudio;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Periodo tarifario (P1 a P6) de cada hora del año tipo, según el calendario de la Circular 3/2020 de la CNMC para la península.
 * Baleares, Canarias, Ceuta y Melilla tienen otras temporadas y no se distinguen aquí. Los sábados, domingos y festivos nacionales de
 * fecha fija son P6 (P3 en la 2.0TD); los festivos autonómicos, locales y los de fecha variable se tratan como laborables.
 */
public final class Periodos {

    private static final Pattern TARIFA = Pattern.compile("([236])\s*[.,]\s*([01])\s*TD");
    /** Las tarifas de acceso que se conocen, en su orden. */
    public static final java.util.List<String> TARIFAS = java.util.List.of("2.0TD", "3.0TD", "6.1TD");

    /** Festivos nacionales de fecha fija (mes 0-11 * 100 + día): 1 y 6 de enero, 1 de mayo, 15 de agosto, 12 de octubre, 1 de noviembre, 6, 8 y 25 de diciembre. */
    private static final Set<Integer> FESTIVOS = Set.of(1, 6, 401, 715, 912, 1001, 1106, 1108, 1125);

    /** Temporada de cada mes (0-11) en 3.0TD y 6.xTD: 0 alta, 1 media alta, 2 media, 3 baja. */
    private static final int[] TEMPORADA = {0, 0, 1, 3, 3, 2, 0, 2, 2, 3, 1, 0};

    private Periodos() {
    }

    /** La tarifa de acceso escrita como en la tabla de precios («3.0TD»), o null si no es una de las seis que se conocen. */
    public static String normalizar(String tarifa) {
        if (tarifa == null) return null;
        Matcher m = TARIFA.matcher(tarifa.toUpperCase(Locale.ROOT));
        if (!m.find()) return null;
        String t = m.group(1) + "." + m.group(2) + "TD";
        return TARIFAS.contains(t) ? t : null;
    }

    /** Cuántos periodos de energía tiene la tarifa: 3 la 2.0TD, 6 las demás. */
    public static int periodosEnergia(String tarifa) {
        return "2.0TD".equals(tarifa) ? 3 : 6;
    }

    /** Cuántos periodos de potencia tiene la tarifa: 2 la 2.0TD, 6 las demás. */
    public static int periodosPotencia(String tarifa) {
        return "2.0TD".equals(tarifa) ? 2 : 6;
    }

    public static boolean festivo(int indice) {
        int m = Curva.mes(indice);
        int dia = (indice - Curva.indice(m, 1, 0)) / 24 + 1;
        return FESTIVOS.contains(m * 100 + dia);
    }

    /** Periodo de energía (0 = P1 … 5 = P6) de una hora del año tipo. */
    public static int periodo(String tarifa, int indice) {
        int h = Curva.hora(indice);
        boolean diaValle = !Curva.laborable(indice) || festivo(indice);
        if ("2.0TD".equals(tarifa)) {
            if (diaValle || h < 8) return 2;
            return (h >= 10 && h < 14) || (h >= 18 && h < 22) ? 0 : 1;
        }
        if (diaValle || h < 8) return 5;
        boolean punta = (h >= 9 && h < 14) || (h >= 18 && h < 22);
        return TEMPORADA[Curva.mes(indice)] + (punta ? 0 : 1);
    }

    /** El periodo de cada hora del año tipo. */
    public static int[] delAno(String tarifa) {
        int[] p = new int[Curva.HORAS];
        for (int i = 0; i < Curva.HORAS; i++) p[i] = periodo(tarifa, i);
        return p;
    }
}
