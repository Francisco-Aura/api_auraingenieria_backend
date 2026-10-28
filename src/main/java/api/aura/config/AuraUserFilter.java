package api.aura.config;

import api.aura.entity.Usuario;
import api.aura.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Takes the user authenticated at login from the HTTP session and stores it
 * in AuraUserHolder (ThreadLocal) for the duration of the request.
 * The DataSource wrapper then reads it and sets @aura_usuario_nombre on
 * every JDBC connection, making the value available to MariaDB triggers.
 *
 * <p>The X-Aura-Usuario-Nombre header sent by the frontend is ignored: any
 * client could forge it to act as another user (or as an administrator).</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuraUserFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuraUserFilter.class);

    /** Atributo de sesión con el instante (ms) de la última comprobación contra la BD. */
    static final String ATRIBUTO_VALIDADO = "AURA_USUARIO_VALIDADO";
    /** Atributo de sesión con la huella de la contraseña con la que se entró. */
    public static final String ATRIBUTO_HUELLA = "AURA_USUARIO_HUELLA";
    /** Cada cuánto se relee el usuario de la sesión en la BD. */
    static final long REVALIDAR_CADA_MS = 60_000;
    /** Último aviso en el log de que no se pudo revalidar (uno por minuto como mucho). */
    private static final AtomicLong ultimoAvisoBd = new AtomicLong();

    /**
     * Clave del log con un código corto de la petición y el id del usuario («3f9a1c u7»):
     * todas las líneas que escribe una misma petición llevan el mismo, así se pueden
     * seguir juntas aunque se mezclen con las de otras (logging.pattern.correlation).
     */
    static final String MDC_PETICION = "peticion";

    /** Null en tests que montan el filtro a mano: entonces no se revalida. */
    private final UsuarioRepository usuarios;

    public AuraUserFilter() {
        this(null);
    }

    @Autowired
    public AuraUserFilter(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String codigo = String.format("%06x", ThreadLocalRandom.current().nextInt(0x1000000));
        MDC.put(MDC_PETICION, codigo);
        try {
            // Siempre se fija (null sin sesión): nunca hereda un valor previo del hilo
            UsuarioSesion usuario = revalidar(request, usuarioDeSesion(request));
            AuraUserHolder.set(usuario);
            if (usuario != null && usuario.id() != null) MDC.put(MDC_PETICION, codigo + " u" + usuario.id());
            // Sin sesión solo se lee el cuerpo del login (unos bytes): acotado, para que
            // nadie sin identificar haga que el servidor cargue en memoria cuerpos enormes
            // (el login o un formulario que Spring lee antes de comprobar la sesión).
            filterChain.doFilter(usuario == null ? new CuerpoAcotado(request) : request, response);
        } finally {
            AuraUserHolder.clear();
            MDC.remove(MDC_PETICION);
        }
    }

    /**
     * La sesión dura semanas: cada minuto como mucho se comprueba en la BD que el
     * usuario sigue existiendo (si se borró, la sesión se cierra) y se recogen los
     * cambios de nombre o de nivel (un administrador degradado deja de serlo ya).
     */
    private UsuarioSesion revalidar(HttpServletRequest request, UsuarioSesion usuario) {
        if (usuarios == null || usuario == null || usuario.id() == null) return usuario;
        HttpSession sesion = request.getSession(false);
        if (sesion == null) return usuario;
        long ahora = System.currentTimeMillis();
        try {
            if (sesion.getAttribute(ATRIBUTO_VALIDADO) instanceof Long ultima
                    && ahora - ultima >= 0 && ahora - ultima < REVALIDAR_CADA_MS)
                return usuario;
            Optional<Usuario> actual = usuarios.findById(usuario.id());
            if (actual.isEmpty()) {
                log.info("Sesión cerrada: el usuario {} ya no existe", usuario.id());
                sesion.invalidate();
                return null;
            }
            Usuario u = actual.get();
            String huella = huella(u.getPass());
            Object huellaSesion = sesion.getAttribute(ATRIBUTO_HUELLA);
            if (huellaSesion != null && !huellaSesion.equals(huella)) {
                log.info("Sesión cerrada: la contraseña del usuario {} ha cambiado", usuario.id());
                sesion.invalidate();
                return null;
            }
            if (huellaSesion == null) sesion.setAttribute(ATRIBUTO_HUELLA, huella);
            UsuarioSesion vigente = new UsuarioSesion(u.getId(),
                    u.getNombre() == null ? null : u.getNombre().trim(), u.getNivelAcceso());
            if (!vigente.equals(usuario)) sesion.setAttribute(UsuarioSesion.ATRIBUTO, vigente);
            sesion.setAttribute(ATRIBUTO_VALIDADO, ahora);
            return vigente;
        } catch (IllegalStateException invalidada) {
            return null;
        } catch (RuntimeException errorBd) {
            // Si la BD falla, se sigue con lo que hay en la sesión (la petición fallará igual)
            long previo = ultimoAvisoBd.get();
            if (ahora - previo > REVALIDAR_CADA_MS && ultimoAvisoBd.compareAndSet(previo, ahora))
                log.warn("No se pudo revalidar el usuario de la sesión: {}", errorBd.getMessage());
            return usuario;
        }
    }

    /**
     * Huella corta de la contraseña guardada (nunca la contraseña): sirve para saber
     * si ha cambiado desde que se abrió la sesión.
     */
    public static String huella(String passGuardada) {
        if (passGuardada == null) return "";
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(passGuardada.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Usuario autenticado en la sesión de la petición, o null si no hay sesión iniciada. */
    public static UsuarioSesion usuarioDeSesion(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion == null) return null;
        try {
            return sesion.getAttribute(UsuarioSesion.ATRIBUTO) instanceof UsuarioSesion usuario ? usuario : null;
        } catch (IllegalStateException invalidada) {
            return null;
        }
    }
}
