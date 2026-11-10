package api.aura.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Lee el usuario de las cabeceras. Acepta las de la intranet (X-Aura-Usuario-Nombre) y las que manda hoy el
 * Planificador (X-Usuario, X-Usuario-Id, X-Usuario-Nivel; el nombre va codificado como URL).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UsuarioPlanificadorFiltro extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        try {
            if (!"OPTIONS".equalsIgnoreCase(request.getMethod())) {
                String nombre = request.getHeader("X-Aura-Usuario-Nombre");
                if (nombre == null || nombre.isBlank()) {
                    nombre = decodificar(request.getHeader("X-Usuario"));
                }
                Integer id = entero(request.getHeader("X-Usuario-Id"));
                Integer nivel = entero(request.getHeader("X-Usuario-Nivel"));
                if ((nombre != null && !nombre.isBlank()) || id != null) {
                    UsuarioPlanificadorHolder.set(
                            new UsuarioPlanificadorHolder.Usuario(nombre == null ? "" : nombre.trim(), id, nivel));
                }
            }
            cadena.doFilter(request, response);
        } finally {
            UsuarioPlanificadorHolder.clear();
        }
    }

    private static String decodificar(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return URLDecoder.decode(valor, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return valor;
        }
    }

    private static Integer entero(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
