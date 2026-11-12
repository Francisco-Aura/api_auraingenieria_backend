package api.aura.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;

/**
 * La cookie de sesión se envía con caducidad (server.servlet.session.cookie.max-age)
 * solo al entrar, así que el navegador la tiraba a los 30 días del login aunque el
 * usuario entrase a diario, y le pedía usuario y contraseña a mitad de trabajo.
 * Una vez al día, con la sesión en uso, se vuelve a enviar con la caducidad entera:
 * solo caduca tras 30 días sin usar Aura (igual que la sesión en el servidor).
 */
@Component
public class RenovacionCookieSesion extends OncePerRequestFilter {

    static final String ATRIBUTO = "AURA_COOKIE_RENOVADA";
    static final long CADA_MS = Duration.ofDays(1).toMillis();

    private final String nombre;
    private final Duration caducidad;
    private final boolean secure;
    private final String sameSite;

    public RenovacionCookieSesion(
            @Value("${server.servlet.session.cookie.name:JSESSIONID}") String nombre,
            @Value("${server.servlet.session.cookie.max-age:#{null}}") Duration caducidad,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
            @Value("${server.servlet.session.cookie.same-site:}") String sameSite) {
        this.nombre = nombre;
        this.caducidad = caducidad;
        this.secure = secure;
        this.sameSite = sameSite == null || sameSite.isBlank() ? null
                : sameSite.substring(0, 1).toUpperCase(Locale.ROOT) + sameSite.substring(1).toLowerCase(Locale.ROOT);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (caducidad != null && !caducidad.isZero() && !caducidad.isNegative()
                && !request.getRequestURI().endsWith("/usuarios/logout")) {
            renovarSiToca(request, response);
        }
        chain.doFilter(request, response);
    }

    private void renovarSiToca(HttpServletRequest request, HttpServletResponse response) {
        HttpSession sesion = request.getSession(false);
        if (sesion == null || sesion.getAttribute(UsuarioSesion.ATRIBUTO) == null) return;
        long ahora = System.currentTimeMillis();
        try {
            if (sesion.getAttribute(ATRIBUTO) instanceof Long ultima && ahora - ultima >= 0 && ahora - ultima < CADA_MS) {
                return;
            }
            String ruta = request.getContextPath() == null || request.getContextPath().isEmpty() ? "/" : request.getContextPath();
            ResponseCookie cookie = ResponseCookie.from(nombre, sesion.getId())
                    .path(ruta)
                    .httpOnly(true)
                    .secure(secure)
                    .sameSite(sameSite)
                    .maxAge(caducidad)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            sesion.setAttribute(ATRIBUTO, ahora);
        } catch (IllegalStateException sesionYaInvalidada) {
            // Se cerró mientras tanto: no hay nada que renovar
        }
    }
}
