package api.aura.service.modulo_planificador.dropbox;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Dónde guarda cada proyecto sus documentos en Dropbox (equivale a dropbox_layout.py).
 *
 * <p>Estructura de la empresa: {@code <base>/<año>/<aammdd>_<nombre>/<subcarpeta>/archivo}. Aquí solo hay funciones puras (sin red).
 */
public final class DropboxEstructura {

    /** Carpeta de las facturas: también para los pagos de más de cuatro y los pasos de facturar. */
    public static final String CARPETA_FACTURAS = "05_FACTURAS EMITIDAS";
    /** Carpeta del contrato: ahí se guarda solo el que genera el planificador. */
    public static final String CARPETA_CONTRATO = "01_CONTRATO INSTALACION, MANTENIMIENTO Y OFERTA ECONOMICA";
    /** Se crean siempre al crear la carpeta de un proyecto, con los nombres y el orden de la estructura de la empresa. */
    public static final List<String> SUBCARPETAS_FIJAS = List.of(
            CARPETA_CONTRATO, "02_PROVEEDORES", "03_FOTOS", "04_PROYECTOS", CARPETA_FACTURAS,
            "06_ACCESO A MONITORIZACION", "07_DOCUMENTACION A ENTREGAR A CLIENTE");
    public static final int MAX_COMPONENTE = 120;

    private static final Pattern PROHIBIDOS = Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1f]");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    private DropboxEstructura() {
    }

    private static String recortarBordes(String t) {
        int ini = 0;
        int fin = t.length();
        while (ini < fin && (t.charAt(ini) == ' ' || t.charAt(ini) == '.')) ini++;
        while (fin > ini && (t.charAt(fin - 1) == ' ' || t.charAt(fin - 1) == '.')) fin--;
        return t.substring(ini, fin);
    }

    /** Nombre válido para una carpeta o archivo de Dropbox: sin caracteres prohibidos, espacios juntos y sin punto ni espacio al final. */
    public static String limpiarComponente(String nombre) {
        String limpio = PROHIBIDOS.matcher(nombre == null ? "" : nombre).replaceAll("-");
        limpio = recortarBordes(ESPACIOS.matcher(limpio).replaceAll(" "));
        if (limpio.length() > MAX_COMPONENTE) {
            limpio = limpio.substring(0, MAX_COMPONENTE);
        }
        int fin = limpio.length();
        while (fin > 0 && (limpio.charAt(fin - 1) == ' ' || limpio.charAt(fin - 1) == '.')) fin--;
        return limpio.substring(0, fin);
    }

    /** Nombre de archivo válido para Dropbox; si hay que acortarlo, conserva la extensión. */
    public static String limpiarNombreArchivo(String nombre) {
        String[] trozos = (nombre == null ? "" : nombre.strip()).split("[\\\\/]", -1);
        String base = trozos[trozos.length - 1];
        int punto = base.lastIndexOf('.');
        String stem = punto >= 0 ? base.substring(0, punto) : "";
        String ext = punto >= 0 ? base.substring(punto + 1) : "";
        if (punto < 0 || stem.isEmpty() || ext.length() > 12) {
            String c = limpiarComponente(base);
            return c.isEmpty() ? "archivo" : c;
        }
        ext = limpiarComponente(ext);
        stem = limpiarComponente(stem);
        int tope = MAX_COMPONENTE - ext.length() - 1;
        if (stem.length() > tope) {
            stem = stem.substring(0, Math.max(0, tope));
            int fin = stem.length();
            while (fin > 0 && (stem.charAt(fin - 1) == ' ' || stem.charAt(fin - 1) == '.')) fin--;
            stem = stem.substring(0, fin);
        }
        if (!ext.isEmpty()) {
            return (stem.isEmpty() ? "archivo" : stem) + "." + ext;
        }
        return stem.isEmpty() ? "archivo" : stem;
    }

    /** aammdd_nombre, p. ej. 260201_BONAQUIA, S.L. (CAMPING DEL AGUA). */
    public static String nombreCarpetaProyecto(LocalDate dia, String nombre) {
        String limpio = limpiarComponente(nombre);
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("Falta el nombre para la carpeta del proyecto");
        }
        return String.format("%02d%02d%02d_%s", dia.getYear() % 100, dia.getMonthValue(), dia.getDayOfMonth(), limpio);
    }

    /** Ruta de Dropbox: empieza por «/», sin barras duplicadas ni final. */
    public static String unir(String... partes) {
        StringBuilder sb = new StringBuilder();
        for (String p : partes) {
            if (p == null) continue;
            String t = p.replaceAll("^/+|/+$", "");
            if (!t.isEmpty()) sb.append('/').append(t);
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }

    /** Ruta de la carpeta del proyecto: {@code <base>/<año>/<aammdd_nombre>}. El año es el de la fecha. */
    public static String rutaProyecto(String base, LocalDate dia, String nombre) {
        return unir(base, String.valueOf(dia.getYear()), nombreCarpetaProyecto(dia, nombre));
    }

    public static String rutaSubcarpeta(String proyecto, String subcarpeta) {
        return unir(proyecto, limpiarComponente(subcarpeta));
    }
}
