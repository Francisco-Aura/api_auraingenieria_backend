package api.aura.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import api.aura.config.AuraUserHolder;

/**
 * Identidad del usuario de la sesión para los controladores. La identidad la
 * fija el servidor (sesión autenticada), nunca un id enviado por el cliente.
 */
public final class SesionActual {

    private SesionActual() {
    }

    /** Id del usuario autenticado; 401 si no hay sesión. */
    public static Integer idRequerido() {
        Integer id = AuraUserHolder.getId();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return id;
    }

    /** Lanza 403 si el usuario no es el indicado ni administrador. */
    public static void exigirPropioOAdmin(Integer usuarioId) {
        Integer actual = idRequerido();
        if (!actual.equals(usuarioId) && !AuraUserHolder.esAdmin()) {
            throw new SecurityException("No tiene permiso para acceder a datos de otro usuario");
        }
    }

    /** Lanza 403 si el usuario no es administrador. */
    public static void exigirAdmin() {
        idRequerido();
        if (!AuraUserHolder.esAdmin()) {
            throw new SecurityException("Operación reservada a administradores");
        }
    }

    /**
     * Mensaje apto para el cliente: solo el de las excepciones de negocio
     * (IllegalArgument/IllegalState, que el código lanza con texto para el
     * usuario); el resto (Jackson, JDBC, NPE...) se sustituye por uno genérico.
     */
    public static String mensajeSeguro(Exception e, String porDefecto) {
        if ((e instanceof IllegalArgumentException || e instanceof IllegalStateException
                || e instanceof SecurityException)
                && e.getMessage() != null) {
            return e.getMessage();
        }
        return porDefecto;
    }
}
