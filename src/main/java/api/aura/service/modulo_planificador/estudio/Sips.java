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

    public static Sips leer(byte[] contenido) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {
            Sheet hoja = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(Locale.ROOT);
            Row cab = null, val = null, cabLect = null;
            int filaLect = -1;
            for (int r = hoja.getFirstRowNum(); r <= hoja.getLastRowNum(); r++) {
                Row row = hoja.getRow(r);
                if (row == null) continue;
                if (cab == null && tiene(row, fmt, "Distribuidora")) {
                    cab = row;
                    val = hoja.getRow(r + 1);
                } else if (cabLect == null && tiene(row, fmt, "Fecha Lectura")) {
                    cabLect = row;
                    filaLect = r;
                }
            }
            if (cab == null || val == null) {
                throw new ErrorApi(HttpStatus.BAD_REQUEST, "El fichero no parece un SIPS: no encuentro los datos del suministro.");
            }
            Map<String, String> s = new LinkedHashMap<>();
            Map<String, Double> numeros = new HashMap<>();
            for (int c = 0; c < Math.max(cab.getLastCellNum(), 0); c++) {
                String nombre = fmt.formatCellValue(cab.getCell(c)).strip();
                Cell celda = val.getCell(c);
                if (nombre.isEmpty() || celda == null) continue;
                s.put(nombre, fmt.formatCellValue(celda).strip());
                if (celda.getCellType() == CellType.NUMERIC) numeros.put(nombre, celda.getNumericCellValue());
            }
            // El CUPS va en la primera columna, que en la cabecera no lleva nombre
            String cups = fmt.formatCellValue(val.getCell(0)).strip();
            s.put("CUPS", cups);
            double[] pot = new double[6];
            for (int p = 0; p < 6; p++) pot[p] = numeros.getOrDefault("Potencia Contratada P" + (p + 1), 0.0);
            List<Lectura> lecturas = new ArrayList<>();
            if (cabLect != null) {
                int cDesde = col(cabLect, fmt, "Fecha Lectura Anterior"), cHasta = col(cabLect, fmt, "Fecha Lectura");
                int[] cP = new int[6];
                for (int p = 0; p < 6; p++) cP[p] = col(cabLect, fmt, "Consumo Activa P" + (p + 1));
                for (int r = filaLect + 1; r <= hoja.getLastRowNum(); r++) {
                    Row row = hoja.getRow(r);
                    if (row == null || cHasta < 0 || cDesde < 0) continue;
                    LocalDate hasta = fecha(row.getCell(cHasta)), desde = fecha(row.getCell(cDesde));
                    if (hasta == null || desde == null || !hasta.isAfter(desde)) continue;
                    double[] k = new double[6];
                    for (int p = 0; p < 6; p++) {
                        Cell c = cP[p] < 0 ? null : row.getCell(cP[p]);
                        k[p] = c != null && c.getCellType() == CellType.NUMERIC ? Math.max(0, c.getNumericCellValue()) : 0;
                    }
                    lecturas.add(new Lectura(desde, hasta, k));
                }
                lecturas.sort(Comparator.comparing(Lectura::hasta));
            }
            return new Sips(s, pot, numeros.getOrDefault("kWh Anual", 0.0), lecturas);
        } catch (IOException | RuntimeException e) {
            if (e instanceof ErrorApi api) throw api;
            throw new ErrorApi(HttpStatus.BAD_REQUEST, "No se pudo leer el SIPS: " + e.getMessage());
        }
    }

    private static boolean tiene(Row row, DataFormatter fmt, String texto) {
        return col(row, fmt, texto) >= 0;
    }

    private static int col(Row row, DataFormatter fmt, String texto) {
        for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
            if (fmt.formatCellValue(row.getCell(c)).strip().equalsIgnoreCase(texto)) return c;
        }
        return -1;
    }

    private static LocalDate fecha(Cell c) {
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC) {
            double v = c.getNumericCellValue();
            return v > 20000 && v < 80000 ? DateUtil.getLocalDateTime(v).toLocalDate() : null;
        }
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
