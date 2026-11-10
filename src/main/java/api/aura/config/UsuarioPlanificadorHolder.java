package api.aura.config;

/** Usuario de la petición en curso (ThreadLocal), igual que AuraUserHolder en la intranet. */
public class UsuarioPlanificadorHolder {

    /** Datos del usuario tal como los manda el front. Ningún campo está verificado. */
    public record Usuario(String nombre, Integer id, Integer nivelAcceso) {
    }

    private static final ThreadLocal<Usuario> holder = new ThreadLocal<>();

    public static void set(Usuario usuario) {
        holder.set(usuario);
    }

    public static Usuario get() {
        return holder.get();
    }

    public static void clear() {
        holder.remove();
    }
}
