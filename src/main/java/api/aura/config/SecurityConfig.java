package api.aura.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Seguridad del backend sobre el login propio de Aura (usuario y contraseña
 * contra la tabla Usuarios): el login deja el usuario en la sesión HTTP
 * ({@link UsuarioSesion}) y todo lo demás exige esa sesión.
 *
 * <ul>
 *   <li>Sin sesión iniciada: 401 con {@code {"error": ...}}.</li>
 *   <li>Peticiones que modifican datos: exigen la cabecera
 *       {@code X-Requested-With: XMLHttpRequest} (la añade axios en el frontend).
 *       Un formulario de otra web no puede enviarla, así que corta el CSRF
 *       junto con la cookie de sesión SameSite.</li>
 *   <li>Cabeceras por defecto de Spring Security (nosniff, X-Frame-Options DENY,
 *       Cache-Control no-store, HSTS bajo HTTPS).</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    static final String CABECERA_AJAX = "X-Requested-With";
    private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthorizationManager<RequestAuthorizationContext> sesionIniciada =
                (autenticacion, contexto) ->
                        new AuthorizationDecision(AuraUserFilter.usuarioDeSesion(contexto.getRequest()) != null);

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/login", "/api/usuarios/logout").permitAll()
                        .requestMatchers("/error", "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().access(sesionIniciada))
                .addFilterBefore(new CabeceraAjaxFilter(), AuthorizationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                escribirError(res, HttpServletResponse.SC_UNAUTHORIZED,
                                        "Sesión no iniciada o caducada. Vuelva a iniciar sesión."))
                        .accessDeniedHandler((req, res, ex) ->
                                escribirError(res, HttpServletResponse.SC_FORBIDDEN, "Acceso denegado.")))
                // CSRF: cabecera AJAX obligatoria + cookie SameSite (ver CabeceraAjaxFilter)
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .logout(logout -> logout.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }

    static void escribirError(HttpServletResponse res, int estado, String mensaje) throws IOException {
        res.setStatus(estado);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":\"" + mensaje + "\"}");
    }

    /**
     * Rechaza con 403 las peticiones que modifican datos sin la cabecera
     * {@code X-Requested-With}. /ws/** queda fuera: SockJS no permite cabeceras
     * propias y ya exige la sesión.
     */
    static class CabeceraAjaxFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            boolean modifica = !METODOS_SEGUROS.contains(request.getMethod().toUpperCase());
            String ruta = request.getRequestURI().substring(request.getContextPath().length());
            if (modifica && !ruta.startsWith("/ws/") && !cabeceraPropia(request)) {
                escribirError(response, HttpServletResponse.SC_FORBIDDEN, "Petición no permitida.");
                return;
            }
            chain.doFilter(request, response);
        }

        private static boolean cabeceraPropia(HttpServletRequest request) {
            return "XMLHttpRequest".equalsIgnoreCase(request.getHeader(CABECERA_AJAX));
        }
    }
}
