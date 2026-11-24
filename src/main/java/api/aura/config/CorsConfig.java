package api.aura.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CORS solo para los orígenes de {@code aura.cors.allowed-origins} (lista separada
 * por comas). Por defecto ninguno: el frontend llama a /api desde su mismo origen
 * (proxy de nginx en los servidores, devServer.proxy en desarrollo) y no lo necesita.
 * Antes se aceptaba cualquier origen con credenciales: con la sesión por cookie,
 * cualquier web podría haber leído los datos del usuario conectado.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] origenes;

    public CorsConfig(@Value("${aura.cors.allowed-origins:}") String origenes) {
        this.origenes = Arrays.stream(origenes.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty() && !"*".equals(o))
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        if (origenes.length == 0) return;
        registry.addMapping("/**")
                .allowedOrigins(origenes)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
