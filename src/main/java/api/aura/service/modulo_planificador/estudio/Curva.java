package api.aura.service.modulo_planificador.estudio;

import api.aura.config.ErrorApi;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Consumo horario de un año tipo de 8.760 horas (sin el 29 de febrero). La hora 0 es el 1 de enero a las 0:00. Los días de la semana
 * son los de 2025, que empieza en miércoles; solo sirven para separar laborables y fines de semana. Un fichero con cuartos de hora se
 * suma por horas; si trae más de un año se queda con los últimos 365 días.
 */
public final class Curva {

    public static final int HORAS = 8760;
    private static final int[] DIAS_MES = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    private static final int[] INICIO_MES = new int[13];
    private static final DayOfWeek PRIMER_DIA = DayOfWeek.WEDNESDAY;
    private static final double COBERTURA_MINIMA = 0.90;

    static {
        for (int m = 0; m < 12; m++) INICIO_MES[m + 1] = INICIO_MES[m] + DIAS_MES[m] * 24;
    }

    private final double[] kwh;
    private final boolean estimada;
    private final String origen;

    public Curva(double[] kwh, boolean estimada, String origen) {
        if (kwh.length != HORAS) throw new IllegalArgumentException("Una curva tiene " + HORAS + " horas");
        this.kwh = kwh;
        this.estimada = estimada;
        this.origen = origen;
    }

    public double[] kwh() {
        return kwh;
    }

    public boolean estimada() {
        return estimada;
    }

    public String origen() {
        return origen;
    }

    // ------------------------------------------------------------------ calendario

    public static int mes(int hora) {
        for (int m = 0; m < 12; m++) if (hora < INICIO_MES[m + 1]) return m;
        return 11;
    }

    public static int hora(int indice) {
        return indice % 24;
    }

    public static int dia(int indice) {
        return indice / 24;
    }

    public static int diasDelMes(int mes) {
        return DIAS_MES[mes];
    }

    /** Hora del año tipo de un día del mes (mes 0-11, día 1..) a una hora (0-23). */
    public static int indice(int mes, int dia, int hora) {
        return INICIO_MES[mes] + (dia - 1) * 24 + hora;
    }

    public static boolean laborable(int indice) {
        DayOfWeek d = PRIMER_DIA.plus(dia(indice));
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
    }

    // ------------------------------------------------------------------ resumen

    public double total() {
        double s = 0;
        for (double v : kwh) s += v;
        return s;
    }

    public double maximo() {
        double m = 0;
        for (double v : kwh) m = Math.max(m, v);
        return m;
    }

    public double[] porMes() {
        double[] r = new double[12];
        for (int i = 0; i < HORAS; i++) r[mes(i)] += kwh[i];
        return r;
    }

    /** Potencia media (kW) de cada hora del día, en laborables o en fines de semana. */
    public double[] perfilHorario(boolean laborables) {
        double[] s = new double[24];
        int[] n = new int[24];
        for (int i = 0; i < HORAS; i++) {
            if (laborable(i) == laborables) {
                s[hora(i)] += kwh[i];
                n[hora(i)]++;
            }
        }
        for (int h = 0; h < 24; h++) s[h] = n[h] == 0 ? 0 : s[h] / n[h];
        return s;
    }

    /** Parte del consumo que cae entre las 8:00 y las 19:00 (horas 8 a 18). */
    public double fraccionDiurna() {
        double d = 0;
        for (int i = 0; i < HORAS; i++) if (hora(i) >= 8 && hora(i) < 19) d += kwh[i];
        double t = total();
        return t <= 0 ? 0 : d / t;
    }

    public String comoTexto() {
        StringBuilder sb = new StringBuilder(HORAS * 5);
        for (int i = 0; i < HORAS; i++) {
            if (i > 0) sb.append(',');
            sb.append(Math.round(kwh[i] * 1000));
        }
        return sb.toString();
    }

    public static Curva desdeTexto(String texto, boolean estimada, String origen) {
        String[] t = texto.split(",");
        if (t.length != HORAS) throw new IllegalArgumentException("Curva guardada incompleta");
        double[] k = new double[HORAS];
        for (int i = 0; i < HORAS; i++) k[i] = Long.parseLong(t[i].strip()) / 1000.0;
        return new Curva(k, estimada, origen);
    }

    // ------------------------------------------------------------------ lectura de ficheros

    /** Lee la curva horaria o cuartohoraria que descarga la distribuidora, en CSV o Excel. */
    public static Curva leer(byte[] contenido, String nombre) {
        String n = nombre == null ? "" : nombre.toLowerCase(Locale.ROOT);
        List<String[]> filas;
        try {
            filas = n.endsWith(".xlsx") || n.endsWith(".xls") ? filasExcel(contenido) : filasCsv(contenido);
        } catch (IOException | RuntimeException e) {
            throw new ErrorApi(HttpStatus.BAD_REQUEST, "No se pudo leer el fichero de la curva: " + e.getMessage());
        }
        return desdeFilas(filas, nombre);
    }

    static Curva desdeFilas(List<String[]> filas, String nombre) {
        int cab = -1;
        int cFecha = -1, cHora = -1, cValor = -1;
        for (int i = 0; i < Math.min(filas.size(), 30) && cab < 0; i++) {
            String[] f = filas.get(i);
            int fe = -1, ho = -1, va = -1;
            for (int c = 0; c < f.length; c++) {
                String h = normalizar(f[c]);
                if (fe < 0 && h.startsWith("fecha")) fe = c;
                else if (ho < 0 && (h.equals("hora") || h.startsWith("hora "))) ho = c;
                else if (va < 0 && (h.contains("kwh") || h.contains("consumo") || h.startsWith("ae") || h.contains("activa"))) va = c;
            }
            if (fe >= 0 && va >= 0) {
                cab = i;
                cFecha = fe;
                cHora = ho;
                cValor = va;
            }
        }
        if (cab < 0) {
            throw new ErrorApi(HttpStatus.BAD_REQUEST, "No encuentro las columnas de fecha y consumo (kWh) en el fichero. Debe tener una cabecera con «Fecha», «Hora» (si va aparte) y el consumo en kWh.");
        }
        Map<LocalDateTime, Double> horas = new TreeMap<>();
        for (int i = cab + 1; i < filas.size(); i++) {
            String[] f = filas.get(i);
            if (f.length <= Math.max(cValor, cFecha)) continue;
            LocalDateTime t = instante(f[cFecha], cHora >= 0 && cHora < f.length ? f[cHora] : null);
            Double v = numero(f[cValor]);
            if (t == null || v == null) continue;
            horas.merge(t, v, Double::sum);
        }
        if (horas.isEmpty()) throw new ErrorApi(HttpStatus.BAD_REQUEST, "El fichero no tiene lecturas válidas (fecha y consumo).");
        LocalDateTime ultimo = ((TreeMap<LocalDateTime, Double>) horas).lastKey();
        LocalDateTime desde = ultimo.minusDays(365);
        double[] k = new double[HORAS];
        double[] suma = new double[24];
        int[] cuenta = new int[24];
        boolean[] hay = new boolean[HORAS];
        for (Map.Entry<LocalDateTime, Double> e : horas.entrySet()) {
            LocalDateTime t = e.getKey();
            if (!t.isAfter(desde)) continue;
            if (t.getMonthValue() == 2 && t.getDayOfMonth() == 29) continue;
            int idx = indice(t.getMonthValue() - 1, t.getDayOfMonth(), t.getHour());
            k[idx] += e.getValue();
            hay[idx] = true;
            suma[t.getHour()] += e.getValue();
            cuenta[t.getHour()]++;
        }
        int con = 0;
        for (boolean b : hay) if (b) con++;
        if (con < HORAS * COBERTURA_MINIMA) {
            throw new ErrorApi(HttpStatus.BAD_REQUEST, "La curva solo cubre " + Math.round(100.0 * con / HORAS)
                    + " % de un año (hacen falta al menos 365 días, salvo pequeños huecos).");
        }
        for (int i = 0; i < HORAS; i++) {
            if (!hay[i]) k[i] = cuenta[hora(i)] == 0 ? 0 : suma[hora(i)] / cuenta[hora(i)];
        }
        return new Curva(k, false, nombre == null ? "" : nombre);
    }

    // ------------------------------------------------------------------ utilidades de lectura

    private static final DateTimeFormatter[] FECHAS = {
            DateTimeFormatter.ofPattern("d/M/yyyy"), DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/M/d"), DateTimeFormatter.ofPattern("d/M/yy")};
    private static final DateTimeFormatter[] FECHAS_HORA = {
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm[:ss]"), DateTimeFormatter.ofPattern("yyyy-M-d H:mm[:ss]"),
            DateTimeFormatter.ofPattern("yyyy-M-d'T'H:mm[:ss]"), DateTimeFormatter.ofPattern("d-M-yyyy H:mm[:ss]"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm[:ss]")};

    static LocalDateTime instante(String fecha, String hora) {
        if (fecha == null) return null;
        String f = fecha.strip();
        if (f.isEmpty()) return null;
        // Fecha y hora en la misma celda
        if (f.contains(":")) {
            for (DateTimeFormatter df : FECHAS_HORA) {
                try {
                    return LocalDateTime.parse(f, df).withMinute(0).withSecond(0).withNano(0);
                } catch (DateTimeParseException ignorada) {
                    // se prueba el siguiente formato
                }
            }
            return null;
        }
        LocalDate d = null;
        for (DateTimeFormatter df : FECHAS) {
            try {
                d = LocalDate.parse(f, df);
                break;
            } catch (DateTimeParseException ignorada) {
                // se prueba el siguiente formato
            }
        }
        if (d == null) return null;
        int h = 0;
        if (hora != null && !hora.isBlank()) {
            String t = hora.strip();
            try {
                if (t.contains(":")) {
                    h = Integer.parseInt(t.substring(0, t.indexOf(':')).strip());
                } else {
                    // 1 a 24: la hora que termina (la 1 es de 0:00 a 1:00)
                    int v = (int) Math.round(Double.parseDouble(t.replace(',', '.')));
                    h = v >= 1 && v <= 24 ? v - 1 : v;
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (h < 0 || h > 23) return null;
        return d.atTime(h, 0);
    }

    static Double numero(String s) {
        if (s == null) return null;
        String t = s.strip().replace(" ", "");
        if (t.isEmpty()) return null;
        if (t.contains(",")) t = t.replace(".", "").replace(",", ".");
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String normalizar(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace("﻿", "").replace('_', ' ');
    }

    static List<String[]> filasCsv(byte[] contenido) {
        String texto = new String(contenido, StandardCharsets.UTF_8);
        if (texto.indexOf('�') >= 0) texto = new String(contenido, StandardCharsets.ISO_8859_1);
        String[] lineas = texto.split("\\r?\\n");
        char sep = ';';
        for (String l : lineas) {
            if (l.isBlank()) continue;
            int pc = count(l, ';'), co = count(l, ','), ta = count(l, '\t');
            sep = ta >= pc && ta >= co && ta > 0 ? '\t' : pc >= co ? ';' : ',';
            if (pc + co + ta > 0) break;
        }
        List<String[]> r = new ArrayList<>(lineas.length);
        for (String l : lineas) {
            if (l.isBlank()) continue;
            String[] c = l.split(java.util.regex.Pattern.quote(String.valueOf(sep)), -1);
            for (int i = 0; i < c.length; i++) c[i] = c[i].strip().replaceAll("^\"|\"$", "");
            r.add(c);
        }
        return r;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    static List<String[]> filasExcel(byte[] contenido) throws IOException {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {
            Sheet hoja = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(Locale.ROOT);
            List<String[]> r = new ArrayList<>();
            for (Row row : hoja) {
                int n = Math.max(row.getLastCellNum(), 0);
                String[] c = new String[n];
                for (int i = 0; i < n; i++) {
                    Cell cell = row.getCell(i);
                    if (cell == null) {
                        c[i] = "";
                    } else if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                        LocalDateTime t = cell.getLocalDateTimeCellValue();
                        c[i] = t.getHour() == 0 && t.getMinute() == 0 ? t.toLocalDate().toString() : t.toString();
                    } else if (cell.getCellType() == CellType.NUMERIC) {
                        c[i] = String.valueOf(cell.getNumericCellValue());
                    } else {
                        c[i] = fmt.formatCellValue(cell).strip();
                    }
                }
                r.add(c);
            }
            return r;
        }
    }
}
