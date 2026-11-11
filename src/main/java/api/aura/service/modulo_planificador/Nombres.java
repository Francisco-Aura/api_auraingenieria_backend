package api.aura.service.modulo_planificador;

import java.util.Collection;

/** Utilidades de nombres de marcas y proveedores (equivale a canonical() de brands.py). */
public final class Nombres {

    private Nombres() {
    }

    /** Espacios de más fuera. */
    public static String limpiar(String nombre) {
        return nombre == null ? "" : String.join(" ", nombre.trim().split("\\s+")).trim();
    }

    /** El nombre tal como está en la lista (sin distinguir mayúsculas ni espacios de más), o null. */
    public static String canonico(String nombre, Collection<String> conocidos) {
        String clave = limpiar(nombre).toLowerCase();
        return conocidos.stream().filter(c -> c.toLowerCase().equals(clave)).findFirst().orElse(null);
    }
}
