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
                .anyRequest().permitAll()  // Permitir TODO sin login
            )
            .csrf().disable()             // Desactivar CSRF (opcional si no usas formularios)
            .formLogin().disable()        // Desactivar formulario login
            .httpBasic().disable();       // Desactivar login básico

        return http.build();
    }
}
