package api.aura.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Permitir CORS desde cualquier origen para todos los endpoints (DESARROLLO)
        registry.addMapping("/**")
                .allowedOriginPatterns("*") // Cambiar esto a orígenes específicos en producción
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Métodos HTTP permitidos
                .allowCredentials(true); // Permitir el uso de cookies o autenticación
    }
}
