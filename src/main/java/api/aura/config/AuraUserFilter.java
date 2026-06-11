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

/**
 * Reads the X-Aura-Usuario-Nombre header sent by the frontend and stores
 * it in AuraUserHolder (ThreadLocal) for the duration of the request.
 * The DataSource wrapper then reads it and sets @aura_usuario_nombre on
 * every JDBC connection, making the value available to MariaDB triggers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuraUserFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Ignorar solicitudes OPTIONS, ya que no suelen llevar el encabezado de usuario
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String nombre = request.getHeader("X-Aura-Usuario-Nombre");
            if (nombre != null && !nombre.isBlank()) {
                AuraUserHolder.set(nombre.trim());
            }
            filterChain.doFilter(request, response);
        } finally {
            AuraUserHolder.clear();
        }
    }
}
