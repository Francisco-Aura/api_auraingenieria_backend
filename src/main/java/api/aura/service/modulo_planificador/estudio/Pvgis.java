package api.aura.service.modulo_planificador.estudio;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Lectura de la respuesta horaria de PVGIS (seriescalc) y paso a nuestro año tipo de 8760 horas en hora local. */
public final class Pvgis {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("yyyyMMdd:HHmm");
    private static final ZoneId ESPANA = ZoneId.of("Europe/Madrid");

    private Pvgis() {
    }

    /**
     * Promedia los años que trae la respuesta por (mes, día, hora local) y devuelve kWh por kWp y hora. El 29 de febrero se descarta;
     * una hora que falta (cambio de hora) toma la anterior.
     */
    public static double[] anoTipo(JsonNode respuesta) {
        JsonNode horas = respuesta.path("outputs").path("hourly");
        if (!horas.isArray() || horas.size() < 8000) throw new IllegalArgumentException("PVGIS no devolvió la serie horaria.");
        double[] suma = new double[Curva.HORAS];
        int[] cuenta = new int[Curva.HORAS];
        for (JsonNode h : horas) {
            LocalDateTime utc = LocalDateTime.parse(h.path("time").asText(), HORA);
            LocalDateTime local = utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ESPANA).toLocalDateTime();
            if (local.getMonthValue() == 2 && local.getDayOfMonth() == 29) continue;
            int i = Curva.indice(local.getMonthValue() - 1, local.getDayOfMonth(), local.getHour());
            suma[i] += Math.max(0, h.path("P").asDouble(0)) / 1000.0;
            cuenta[i]++;
        }
        double[] r = new double[Curva.HORAS];
        for (int i = 0; i < Curva.HORAS; i++) r[i] = cuenta[i] > 0 ? suma[i] / cuenta[i] : (i > 0 ? r[i - 1] : 0);
        return r;
    }

    /** Media de varias series (para el este-oeste: una mitad mirando al este y la otra al oeste). */
    public static double[] media(double[]... series) {
        double[] r = new double[Curva.HORAS];
        for (double[] s : series) for (int i = 0; i < r.length; i++) r[i] += s[i] / series.length;
        return r;
    }

    public static String comoTexto(double[] v) {
        StringBuilder b = new StringBuilder(v.length * 6);
        for (int i = 0; i < v.length; i++) {
            if (i > 0) b.append(',');
            b.append(Math.round(v[i] * 10000) / 10000.0);
        }
        return b.toString();
    }

    public static double[] desdeTexto(String t) {
        String[] p = t.split(",");
        if (p.length != Curva.HORAS) throw new IllegalArgumentException("Serie guardada incompleta.");
        double[] v = new double[Curva.HORAS];
        for (int i = 0; i < v.length; i++) v[i] = Double.parseDouble(p[i]);
        return v;
    }
}
