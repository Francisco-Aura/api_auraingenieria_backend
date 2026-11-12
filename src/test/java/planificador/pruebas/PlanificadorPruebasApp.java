package planificador.pruebas;

import api.aura.config.JacksonConfig;
import api.aura.config.PlanificadorExceptionHandler;
import api.aura.config.UsuarioPlanificadorFiltro;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Aplicación mínima para probar solo el módulo Planificador (sin el resto de la intranet): sus controladores, servicios, entidades y
 * repositorios, con H2. Está fuera del paquete api.aura a propósito: así el contexto completo de la intranet no la recoge. Quedan fuera los puentes con la intranet ({@code ...planificador.intranet}), que necesitan sus servicios.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = {"api.aura.controller.modulo_planificador", "api.aura.service.modulo_planificador"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*[.]intranet[.].*|.*PlanificadorPruebas.*"))
@EntityScan("api.aura.entity.modulo_planificador")
@EnableJpaRepositories("api.aura.repository.modulo_planificador")
@Import({JacksonConfig.class, UsuarioPlanificadorFiltro.class, PlanificadorExceptionHandler.class})
public class PlanificadorPruebasApp {

    /**
     * Aquí se prueba el módulo, no el login: todo abierto, como cuando se escribieron estas pruebas. La seguridad
     * de verdad (sesión de la intranet) está en SecurityConfig y se prueba en SecurityConfigTest.
     */
    @Bean
    SecurityFilterChain sinLogin(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // Igual que SecurityConfig: los PDF se pueden incrustar solo desde la propia intranet
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }
}
