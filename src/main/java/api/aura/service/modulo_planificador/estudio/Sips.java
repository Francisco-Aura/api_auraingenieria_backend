package api.aura.service.modulo_planificador.estudio;

import api.aura.config.ErrorApi;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/**
 * Datos del SIPS de la distribuidora (Excel): el suministro (CUPS, tarifa, potencias contratadas, consumos anuales) y las lecturas
 * mensuales por periodo. No trae curva horaria; con las lecturas se puede estimar una (ver {@link CurvaEstimada}).
 */
public final class Sips {

    /** Una lectura: el consumo activo (kWh) de cada periodo P1..P6 entre dos fechas. */
    public record Lectura(LocalDate desde, LocalDate hasta, double[] kwh) {
        public double total() {
            double s = 0;
            for (double v : kwh) s += v;
            return s;
        }
    }

    private final Map<String, String> suministro;
    private final double[] potencias;
    private final double kwhAnual;
    private final List<Lectura> lecturas;

    private Sips(Map<String, String> suministro, double[] potencias, double kwhAnual, List<Lectura> lecturas) {
        this.suministro = suministro;
        this.potencias = potencias;
        this.kwhAnual = kwhAnual;
        this.lecturas = lecturas;
    }

    public Map<String, String> suministro() {
        return suministro;
    }

    public double[] potencias() {
        return potencias;
    }

    public double kwhAnual() {
        return kwhAnual;
    }

    public List<Lectura> lecturas() {
        return lecturas;
    }

    // Las cabeceras se reconocen por su sentido y no por su texto exacto: sin tildes ni mayúsculas, con cualquier orden de palabras
    // razonable («Potencia Contratada P1», «Pot. contratada (kW) P1»…), para que valga el Excel aunque cambie un poco de un año a otro.
    private static final java.util.regex.Pattern P_DISTRIBUIDORA = java.util.regex.Pattern.compile("^(nombre )?(de )?(la )?distribuidora$|^distribuidor$");
    private static final java.util.regex.Pattern P_DESDE = java.util.regex.Pattern.compile("^fecha.*(anterior|inicial|desde|inicio).*$|^lectura anterior$");
    private static final java.util.regex.Pattern P_HASTA = java.util.regex.Pattern.compile("^fecha( de)?( la)? lectura$|^fecha (final|fin|hasta)$|^lectura$");
    private static final java.util.regex.Pattern P_PERIODO = java.util.regex.Pattern.compile("(?:\\bp|periodo\\s*)\\s*([1-6])\\b");
    private static final java.util.regex.Pattern P_TARIFA = java.util.regex.Pattern.compile("^tarifa( de acceso)?( atr)?$|^peaje( de acceso)?$");
    private static final java.util.regex.Pattern P_LOCALIDAD = java.util.regex.Pattern.compile("^(localidad|municipio|poblacion)( suministro| punto( de suministro)?)?$");
    private static final java.util.regex.Pattern P_PROVINCIA = java.util.regex.Pattern.compile("^provincia( suministro| punto( de suministro)?)?$");
    private static final java.util.regex.Pattern P_CNAE = java.util.regex.Pattern.compile("^cnae( .*)?$|^actividad( economica)?$");
    private static final java.util.regex.Pattern P_ANUAL = java.util.regex.Pattern.compile("kwh.*anual|anual.*kwh|^consumo anual$");

    /** El periodo (1 a 6) que nombra una cabecera, o 0 si no nombra ninguno. */
    private static int periodoDe(String h) {
        java.util.regex.Matcher m = P_PERIODO.matcher(h);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** Una columna de consumo activo de un periodo: lleva P1..P6 y no es de reactiva, maxímetro, potencia ni excedentes. */
    private static boolean esConsumoActivo(String h) {
        return periodoDe(h) > 0 && (h.contains("activa") || h.contains("consumo") || h.contains("energia"))
                && !h.contains("reactiv") && !h.contains("maximetro") && !h.contains("potencia") && !h.contains("excedente");
    }

    private static boolean esCabeceraSuministro(Row row, DataFormatter fmt) {
        boolean cups = false, tarifa = false;
        for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
            String h = normalizar(fmt.formatCellValue(row.getCell(c)));
            if (P_DISTRIBUIDORA.matcher(h).matches()) return true;
            cups |= h.equals("cups");
            tarifa |= P_TARIFA.matcher(h).matches();
        }
        return cups && tarifa;
    }

    private static boolean esCabeceraLecturas(Row row, DataFormatter fmt) {
        boolean hasta = false, periodos = false;
        for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
            String h = normalizar(fmt.formatCellValue(row.getCell(c)));
            hasta |= P_HASTA.matcher(h).matches();
            periodos |= esConsumoActivo(h);
        }
        return hasta && periodos;
    }

    private static Row filaConDatos(Sheet hoja, int desde) {
        for (int r = desde; r <= Math.min(hoja.getLastRowNum(), desde + 5); r++) {
            Row row = hoja.getRow(r);
            if (row != null && row.getPhysicalNumberOfCells() > 1) return row;
        }
        return null;
    }

    public static Sips leer(byte[] contenido) {
        if (esTablaHtml(contenido)) return leerTabla(contenido);
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {
            DataFormatter fmt = new DataFormatter(Locale.ROOT);
            Sheet hoja = null;
            Row cab = null, val = null, cabLect = null;
            int filaCab = -1, filaLect = -1;
            // Se mira cada hoja hasta dar con la de los datos del suministro (casi siempre es la primera)
            for (int i = 0; i < wb.getNumberOfSheets() && cab == null; i++) {
                Sheet h = wb.getSheetAt(i);
                for (int r = h.getFirstRowNum(); r <= h.getLastRowNum() && cab == null; r++) {
                    Row row = h.getRow(r);
                    if (row != null && esCabeceraSuministro(row, fmt)) {
                        hoja = h;
                        cab = row;
                        filaCab = r;
                        val = filaConDatos(h, r + 1);
                    }
                }
            }
            if (cab == null || val == null) {
                throw new ErrorApi(HttpStatus.BAD_REQUEST, "El fichero no parece un SIPS: no encuentro los datos del suministro.");
            }
            for (int r = filaCab + 1; r <= hoja.getLastRowNum() && cabLect == null; r++) {
                Row row = hoja.getRow(r);
                if (row != null && esCabeceraLecturas(row, fmt)) {
                    cabLect = row;
                    filaLect = r;
                }
            }
            Map<String, String> s = new LinkedHashMap<>();
            double[] pot = new double[6];
            double anual = 0;
            for (int c = 0; c < Math.max(cab.getLastCellNum(), 0); c++) {
                String nombre = fmt.formatCellValue(cab.getCell(c)).strip();
                Cell celda = val.getCell(c);
                if (nombre.isEmpty() || celda == null) continue;
                String valor = fmt.formatCellValue(celda).strip();
                s.put(nombre, valor);
                String h = normalizar(nombre);
                // Los nombres de siempre, para quien los lea por ellos
                if (P_TARIFA.matcher(h).matches()) s.put("Tarifa", valor);
                else if (P_LOCALIDAD.matcher(h).matches()) s.put("Localidad", valor);
                else if (P_PROVINCIA.matcher(h).matches()) s.put("Provincia", valor);
                else if (P_CNAE.matcher(h).matches()) s.put("CNAE", valor);
                int p = periodoDe(h);
                if (p > 0 && h.contains("potencia") && h.contains("contrat")) pot[p - 1] = numero(celda);
                else if (P_ANUAL.matcher(h).find() && !h.contains("anterior")) anual = numero(celda);
            }
            // El CUPS: la celda con ese formato (esté en la columna que esté); si no, la primera columna
            String cups = "";
            for (int c = 0; c < Math.max(val.getLastCellNum(), 0) && cups.isEmpty(); c++) {
                java.util.regex.Matcher m = CUPS.matcher(fmt.formatCellValue(val.getCell(c)).toUpperCase(Locale.ROOT));
                if (m.find()) cups = m.group();
            }
            if (cups.isEmpty()) cups = fmt.formatCellValue(val.getCell(0)).strip();
            s.put("CUPS", cups);
            List<Lectura> lecturas = new ArrayList<>();
            if (cabLect != null) {
                int cDesde = -1, cHasta = -1;
                int[] cP = new int[6];
                Arrays.fill(cP, -1);
                for (int c = 0; c < Math.max(cabLect.getLastCellNum(), 0); c++) {
                    String h = normalizar(fmt.formatCellValue(cabLect.getCell(c)));
                    if (P_DESDE.matcher(h).matches()) cDesde = c;
                    else if (P_HASTA.matcher(h).matches()) cHasta = c;
                    else if (esConsumoActivo(h) && cP[periodoDe(h) - 1] < 0) cP[periodoDe(h) - 1] = c;
                }
                for (int r = filaLect + 1; r <= hoja.getLastRowNum(); r++) {
                    Row row = hoja.getRow(r);
                    if (row == null || cHasta < 0 || cDesde < 0) continue;
                    LocalDate hasta = fecha(row.getCell(cHasta)), desde = fecha(row.getCell(cDesde));
                    if (hasta == null || desde == null || !hasta.isAfter(desde)) continue;
                    double[] k = new double[6];
                    for (int p = 0; p < 6; p++) k[p] = cP[p] < 0 ? 0 : Math.max(0, numero(row.getCell(cP[p])));
                    lecturas.add(new Lectura(desde, hasta, k));
                }
                lecturas.sort(Comparator.comparing(Lectura::hasta));
            }
            if (anual <= 0 && !lecturas.isEmpty()) {
                LocalDate corte = lecturas.get(lecturas.size() - 1).hasta().minusDays(365);
                for (Lectura l : lecturas) if (l.hasta().isAfter(corte)) anual += l.total();
            }
            return new Sips(s, pot, anual, lecturas);
        } catch (IOException | RuntimeException e) {
            if (e instanceof ErrorApi api) throw api;
            throw new ErrorApi(HttpStatus.BAD_REQUEST, "No se pudo leer el SIPS: " + e.getMessage());
        }
    }

    /** El número de una celda, sea número o texto («2.059,5» o «2059.5»); vacío o ilegible, 0. */
    private static double numero(Cell c) {
        if (c == null) return 0;
        if (c.getCellType() == CellType.NUMERIC) return c.getNumericCellValue();
        if (c.getCellType() == CellType.STRING) return numeroTabla(c.getStringCellValue());
        return 0;
    }

    // ------------------------------------------------------------------ SIPS como tabla web
    // La web de la distribuidora da el SIPS como un «.xls» que en realidad es una página HTML con una tabla (CUPS, fechas de lectura y
    // consumos por periodo, con decimales en coma). Excel la abre, pero no es un libro de Excel: se reconoce por su contenido.

    private static final java.util.regex.Pattern CUPS = java.util.regex.Pattern.compile("\\bES\\d{16}[A-Z0-9]{0,4}\\b");

    /** Un libro de Excel empieza por «PK» (xlsx) o por un bloque binario (xls); una página web, por «&lt;». */
    static boolean esTablaHtml(byte[] contenido) {
        String inicio = new String(contenido, 0, Math.min(contenido.length, 2048), java.nio.charset.StandardCharsets.UTF_8).stripLeading();
        if (inicio.startsWith("﻿")) inicio = inicio.substring(1).stripLeading();
        inicio = inicio.toLowerCase(Locale.ROOT);
        return inicio.startsWith("<") && (inicio.contains("<html") || inicio.contains("<table"));
    }

    private static Sips leerTabla(byte[] contenido) {
        List<List<String>> filas = filasDeTabla(new String(contenido, java.nio.charset.StandardCharsets.UTF_8));
        int cab = -1, cDesde = -1, cHasta = -1;
        int[] cP = new int[6];
        for (int i = 0; i < filas.size() && cab < 0; i++) {
            int desde = -1, hasta = -1, periodos = 0;
            int[] p = new int[6];
            Arrays.fill(p, -1);
            List<String> f = filas.get(i);
            for (int c = 0; c < f.size(); c++) {
                String h = normalizar(f.get(c));
                if (h.contains("fecha") && (h.contains("inicial") || h.contains("anterior") || h.contains("desde"))) desde = c;
                else if (h.contains("fecha") && (h.contains("final") || h.contains("hasta") || h.equals("fecha lectura"))) hasta = c;
                else if (h.contains("activa") && !h.contains("reactiva")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\bp([1-6])\\b").matcher(h);
                    if (m.find()) {
                        p[Integer.parseInt(m.group(1)) - 1] = c;
                        periodos++;
                    }
                }
            }
            if (desde >= 0 && hasta >= 0 && periodos > 0) {
                cab = i;
                cDesde = desde;
                cHasta = hasta;
                cP = p;
            }
        }
        if (cab < 0) {
            throw new ErrorApi(HttpStatus.BAD_REQUEST, "El fichero no parece un SIPS: no encuentro las fechas de lectura y el consumo activo de cada periodo.");
        }
        String cups = "";
        for (List<String> f : filas) {
            for (String celda : f) {
                java.util.regex.Matcher m = CUPS.matcher(celda.toUpperCase(Locale.ROOT));
                if (cups.isEmpty() && m.find()) cups = m.group();
            }
        }
        List<Lectura> lecturas = new ArrayList<>();
        for (int i = cab + 1; i < filas.size(); i++) {
            List<String> f = filas.get(i);
            LocalDate desde = fechaTabla(celda(f, cDesde)), hasta = fechaTabla(celda(f, cHasta));
            if (desde == null || hasta == null || !hasta.isAfter(desde)) continue;
            double[] k = new double[6];
            for (int p = 0; p < 6; p++) k[p] = cP[p] < 0 ? 0 : Math.max(0, numeroTabla(celda(f, cP[p])));
            lecturas.add(new Lectura(desde, hasta, k));
        }
        if (lecturas.isEmpty()) throw new ErrorApi(HttpStatus.BAD_REQUEST, "El SIPS no trae lecturas de consumo.");
        lecturas.sort(Comparator.comparing(Lectura::hasta));
        // Esta tabla no trae el suministro (tarifa, localidad, potencias) ni el consumo anual: este sale de las lecturas del último año
        LocalDate corte = lecturas.get(lecturas.size() - 1).hasta().minusDays(365);
        double anual = 0;
        for (Lectura l : lecturas) if (l.hasta().isAfter(corte)) anual += l.total();
        Map<String, String> suministro = new LinkedHashMap<>();
        suministro.put("CUPS", cups);
        return new Sips(suministro, new double[0], anual, lecturas);
    }

    private static String celda(List<String> fila, int c) {
        return c >= 0 && c < fila.size() ? fila.get(c) : "";
    }

    /** Las filas de la tabla con el texto de cada celda, sin etiquetas. */
    static List<List<String>> filasDeTabla(String html) {
        List<List<String>> filas = new ArrayList<>();
        java.util.regex.Matcher tr = java.util.regex.Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>").matcher(html);
        while (tr.find()) {
            List<String> celdas = new ArrayList<>();
            java.util.regex.Matcher td = java.util.regex.Pattern.compile("(?is)<t[dh][^>]*>(.*?)</t[dh]>").matcher(tr.group(1));
            while (td.find()) celdas.add(textoHtml(td.group(1)));
            if (!celdas.isEmpty()) filas.add(celdas);
        }
        return filas;
    }

    private static String textoHtml(String html) {
        String t = html.replaceAll("(?s)<[^>]*>", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        return t.replace(' ', ' ').strip();
    }

    /** Minúsculas, sin tildes y con un solo espacio entre palabras: «P1 Máximetro» y «p1  maximetro» son lo mismo. */
    private static String normalizar(String s) {
        String n = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    /** «2023-08-31T00:00:00» o «31/08/2023». */
    private static LocalDate fechaTabla(String s) {
        String t = s == null ? "" : s.strip();
        try {
            if (t.matches("^\\d{4}-\\d{2}-\\d{2}.*")) return LocalDate.parse(t.substring(0, 10));
            if (t.matches("^\\d{1,2}/\\d{1,2}/\\d{4}.*")) {
                String[] d = t.split("[ T]")[0].split("/");
                return LocalDate.of(Integer.parseInt(d[2]), Integer.parseInt(d[1]), Integer.parseInt(d[0]));
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    /** Los consumos van con decimales en coma («2059,000»); un hueco cuenta como 0. */
    private static double numeroTabla(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return api.aura.service.modulo_planificador.Numeros.aNumero(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }


    private static LocalDate fecha(Cell c) {
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC) {
            double v = c.getNumericCellValue();
            return v > 20000 && v < 80000 ? DateUtil.getLocalDateTime(v).toLocalDate() : null;
        }
        if (c.getCellType() == CellType.STRING) return fechaTabla(c.getStringCellValue());
        return null;
    }

    /** Lo que se guarda con el estudio para poder estimar la curva sin volver a subir el fichero. */
    public Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("suministro", suministro);
        List<Double> p = new ArrayList<>();
        for (double v : potencias) p.add(v);
        m.put("potencias", p);
        m.put("kwhAnual", kwhAnual);
        List<Map<String, Object>> l = new ArrayList<>();
        for (Lectura x : lecturas) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("desde", x.desde().toString());
            f.put("hasta", x.hasta().toString());
            List<Double> k = new ArrayList<>();
            for (double v : x.kwh()) k.add(v);
            f.put("kwh", k);
            l.add(f);
        }
        m.put("lecturas", l);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static List<Lectura> lecturasDeMapa(Object lecturas) {
        List<Lectura> r = new ArrayList<>();
        if (!(lecturas instanceof List<?> l)) return r;
        for (Object o : l) {
            if (!(o instanceof Map<?, ?> m)) continue;
            List<Object> k = (List<Object>) m.get("kwh");
            double[] v = new double[6];
            for (int i = 0; i < 6 && k != null && i < k.size(); i++) v[i] = ((Number) k.get(i)).doubleValue();
            r.add(new Lectura(LocalDate.parse(String.valueOf(m.get("desde"))), LocalDate.parse(String.valueOf(m.get("hasta"))), v));
        }
        return r;
    }
}
