package api.aura.util;

/**
 * Nombres de archivo que llegan del navegador. Pueden traer caracteres de control (un
 * salto de línea codificado en {@code filename*=UTF-8''...}): se guardaban así y, al
 * escribirlos en el log, permitían colar líneas falsas (p. ej. un «login correcto»).
 */
public final class NombresArchivo {

    private NombresArchivo() {}

    /** El nombre sin caracteres de control ni separadores de línea Unicode. */
    public static String sinControl(String nombre) {
        if (nombre == null) return null;
        return nombre.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", "");
    }

    /**
     * El nombre recortado para que ocupe como mucho {@code maximoBytes} en UTF-8 (el límite
     * de un nombre de archivo en disco), conservando la extensión. Si ya cabe, tal cual.
     */
    public static String recortarBytes(String nombre, int maximoBytes) {
        if (nombre == null || bytes(nombre) <= maximoBytes) return nombre;
        int punto = nombre.lastIndexOf('.');
        String extension = punto > 0 && bytes(nombre.substring(punto)) <= 16 ? nombre.substring(punto) : "";
        String base = nombre.substring(0, nombre.length() - extension.length());
        int espacio = maximoBytes - bytes(extension);
        StringBuilder recortado = new StringBuilder();
        int usados = 0;
        for (int i = 0; i < base.length(); ) {
            int caracter = base.codePointAt(i);
            int tamano = bytes(new String(Character.toChars(caracter)));
            if (usados + tamano > espacio) break;
            recortado.appendCodePoint(caracter);
            usados += tamano;
            i += Character.charCount(caracter);
        }
        return recortado + extension;
    }

    private static int bytes(String texto) {
        return texto.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
}
