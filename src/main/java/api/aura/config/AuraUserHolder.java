package api.aura.config;

/**
 * Holds the authenticated user (taken from the HTTP session) in a ThreadLocal
 * for the duration of the request. The name is forwarded to the database
 * session variable @aura_usuario_nombre; id and access level are used for
 * server-side authorization.
 */
public class AuraUserHolder {

    private static final ThreadLocal<UsuarioSesion> holder = new ThreadLocal<>();

    public static void set(UsuarioSesion usuario) {
        holder.set(usuario);
    }

    /** Solo el nombre (usado por procesos internos y tests sin sesión). */
    public static void set(String nombre) {
        holder.set(nombre == null ? null : new UsuarioSesion(null, nombre, null));
    }

    public static String get() {
        UsuarioSesion usuario = holder.get();
        return usuario == null ? null : usuario.nombre();
    }

    public static Integer getId() {
        UsuarioSesion usuario = holder.get();
        return usuario == null ? null : usuario.id();
    }

    public static Integer getNivelAcceso() {
        UsuarioSesion usuario = holder.get();
        return usuario == null ? null : usuario.nivelAcceso();
    }

    public static boolean esAdmin() {
        UsuarioSesion usuario = holder.get();
        return usuario != null && usuario.esAdmin();
    }

    public static void clear() {
        holder.remove();
    }
}
