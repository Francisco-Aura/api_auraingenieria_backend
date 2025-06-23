package api.aura.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @SuppressWarnings("null")
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Permitir CORS desde cualquier origen para todos los endpoints (DESARROLLO)
        registry.addMapping("/**")
                .allowedOriginPatterns("*") // Cambiar esto a orígenes específicos en producción
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
                // .allowCredentials(true); // Desactivado para permitir allowedOriginPatterns("*")
    }
}
