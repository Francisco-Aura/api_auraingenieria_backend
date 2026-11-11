package registrohorario.pruebas;

import api.aura.config.JacksonConfig;
import api.aura.config.RegistroHorarioExceptionHandler;
import api.aura.config.SecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Aplicación mínima con solo el módulo Registro Horario, sin base de datos ni el resto de la intranet: sirve para probar su
 * pantalla en local o para pruebas de extremo a extremo. Está fuera del paquete api.aura a propósito: así el contexto completo
 * de la intranet no la recoge. Arranque (ruta de las empresas por la variable REGISTRO_EMPRESAS, puerto por server.port):
 * {@code java -cp ... registrohorario.pruebas.RegistroHorarioPruebasApp --server.port=8986}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@ComponentScan(basePackages = {"api.aura.controller.modulo_registro_horario", "api.aura.service.modulo_registro_horario"})
@Import({JacksonConfig.class, SecurityConfig.class, RegistroHorarioExceptionHandler.class})
public class RegistroHorarioPruebasApp {

    public static void main(String[] args) {
        SpringApplication.run(RegistroHorarioPruebasApp.class, args);
    }
}
