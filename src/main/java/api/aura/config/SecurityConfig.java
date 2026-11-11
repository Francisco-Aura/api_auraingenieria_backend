package api.aura.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll())
                // Por defecto Spring envía X-Frame-Options: DENY y el navegador no deja incrustar ni los PDF propios (fichas técnicas,
                // presupuestos) en un iframe de la intranet. Con SAMEORIGIN solo se pueden incrustar desde la propia intranet.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .csrf(csrf -> csrf.disable()) // Desactivar CSRF
                .formLogin(form -> form.disable()) // Desactivar formulario login
                .httpBasic(basic -> basic.disable()); // Desactivar login básico

        return http.build();
    }
}
