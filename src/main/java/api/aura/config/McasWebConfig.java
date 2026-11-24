package api.aura.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class McasWebConfig implements WebMvcConfigurer {
    /**
     * Toda la configuración de módulos pasa por el interceptor: los patrones de
     * ruta distinguen mayúsculas y el código del indicador no, así que el filtrado
     * de los indicadores MCAS se hace dentro de {@link McasAccessInterceptor}.
     */
    public static final String[] RUTAS_INTERCEPTADAS =
            { "/api/mcas/**", "/api/configuracion-modulos", "/api/configuracion-modulos/**" };

    private final McasAccessInterceptor interceptor;

    public McasWebConfig(McasAccessInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns(RUTAS_INTERCEPTADAS);
    }
}
