package api.aura.config;

import java.io.Serializable;

/**
 * Usuario autenticado guardado en la sesión HTTP tras el login.
 *
 * <p>Es la única fuente de identidad del backend: la cabecera
 * {@code X-Aura-Usuario-Nombre} que envía el frontend la podía falsificar
 * cualquiera, así que ya no se usa para identificar ni autorizar.</p>
 */
public record UsuarioSesion(Integer id, String nombre, Integer nivelAcceso) implements Serializable {

    /** Atributo de la {@link jakarta.servlet.http.HttpSession} donde se guarda. */
    public static final String ATRIBUTO = "AURA_USUARIO";

    public boolean esAdmin() {
        return Integer.valueOf(1).equals(nivelAcceso);
    }
}
