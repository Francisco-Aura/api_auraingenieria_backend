package api.aura.controller;

import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

/**
 * Cabeceras seguras para servir archivos subidos por usuarios: siempre como
 * descarga, sin tipos que el navegador pueda ejecutar (HTML, SVG, XML, JS) y
 * sin caracteres que permitan inyectar cabeceras.
 */
public final class DescargaSegura {

    private DescargaSegura() {
    }

    /** Cabeceras de descarga (attachment + nosniff) para el nombre indicado. */
    public static HttpHeaders cabeceras(String nombreArchivo, String porDefecto) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreSeguro(nombreArchivo, porDefecto) + "\"");
        headers.set("X-Content-Type-Options", "nosniff");
        return headers;
    }

    /** Quita comillas, barras invertidas y caracteres de control del nombre. */
    public static String nombreSeguro(String nombre, String porDefecto) {
        if (nombre == null) {
            return porDefecto;
        }
        String limpio = nombre.replaceAll("[\"\\\\\\p{Cntrl}]", "").trim();
        return limpio.isEmpty() ? porDefecto : limpio;
    }

    /**
     * Tipo con el que servir un archivo cuyo tipo declaró el cliente al subirlo.
     * Los tipos activos (HTML, SVG, XML, JavaScript...) o ilegibles se sirven
     * como application/octet-stream para evitar XSS almacenado.
     */
    public static MediaType tipoSeguro(String tipoMime) {
        if (tipoMime == null || tipoMime.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        MediaType tipo;
        try {
            tipo = MediaType.parseMediaType(tipoMime);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        if (tipo.isWildcardType() || tipo.isWildcardSubtype()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        String principal = tipo.getType().toLowerCase(Locale.ROOT);
        String subtipo = tipo.getSubtype().toLowerCase(Locale.ROOT);
        boolean activo = subtipo.contains("html") || subtipo.contains("svg") || subtipo.equals("xml")
                || subtipo.endsWith("+xml") || subtipo.contains("javascript") || subtipo.contains("ecmascript")
                || subtipo.contains("x-shockwave-flash") || principal.equals("multipart")
                || (principal.equals("text") && !subtipo.equals("plain") && !subtipo.equals("csv"));
        // Sin parámetros (charset u otros) heredados del cliente
        return activo ? MediaType.APPLICATION_OCTET_STREAM : new MediaType(principal, subtipo);
    }
}
