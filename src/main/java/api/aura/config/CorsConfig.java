package api.aura.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        // Permitir CORS desde cualquier origen para todos los endpoints (DESARROLLO)
        registry.addMapping("/**")
                .allowedOriginPatterns("*") // Cambiar esto a orígenes específicos en producción
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*") // Incluye X-Aura-Usuario-Nombre y cualquier otro header custom
                .allowCredentials(true);
    }
}
