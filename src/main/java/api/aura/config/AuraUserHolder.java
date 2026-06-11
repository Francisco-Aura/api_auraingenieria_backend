package api.aura.config;

/**
 * Holds the authenticated user's name in a ThreadLocal so it can be
 * forwarded to the database session variable @aura_usuario_nombre.
 */
public class AuraUserHolder {

    private static final ThreadLocal<String> holder = new ThreadLocal<>();

    public static void set(String nombre) {
        holder.set(nombre);
    }

    public static String get() {
        return holder.get();
    }

    public static void clear() {
        holder.remove();
    }
}
