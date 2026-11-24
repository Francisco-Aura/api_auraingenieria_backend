package api.aura.config;

import api.aura.service.mcas.McasAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Locale;
import java.util.Map;

@Component
public class McasAccessInterceptor implements HandlerInterceptor {
    private static final String RUTA_CONFIGURACION = "/api/configuracion-modulos";

    private final McasAccessService access;

    public McasAccessInterceptor(McasAccessService access) {
        this.access = access;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String uri = request.getRequestURI();
        if (uri.startsWith(RUTA_CONFIGURACION)) {
            // Solo los indicadores MCAS exigen administrador; el resto de módulos
            // (p. ej. GESTOR_TAREAS) mantienen su comportamiento.
            if (!esIndicadorMcas(request, uri)) return true;
        } else if ("GET".equalsIgnoreCase(request.getMethod())
                && uri.startsWith("/api/mcas/productos-comerciales")) {
            // Catálogo de productos: lo lee el formulario de contratos.
            return true;
        } else if (handler instanceof HandlerMethod metodo && metodo.hasMethodAnnotation(McasAccesoUsuario.class)) {
            // Resumen y vista previa del contrato: los ve quien lo graba, sin ser administrador.
            access.requireUsuario();
            return true;
        }
        access.requireAdmin();
        return true;
    }

    /**
     * El código se compara sin distinguir mayúsculas y ya decodificado, igual que
     * lo resuelve el controlador: la búsqueda en MariaDB tampoco las distingue,
     * así que {@code mcas_enabled} o {@code %4DCAS_ENABLED} cambiarían la misma
     * fila que {@code MCAS_ENABLED}.
     */
    private boolean esIndicadorMcas(HttpServletRequest request, String uri) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables instanceof Map<?, ?> mapa && mapa.get("codigo") instanceof String codigo)
            return codigo.trim().toUpperCase(Locale.ROOT).startsWith("MCAS");
        return uri.toLowerCase(Locale.ROOT).startsWith(RUTA_CONFIGURACION + "/mcas");
    }
}
