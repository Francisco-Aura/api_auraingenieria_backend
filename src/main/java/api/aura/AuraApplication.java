package api.aura;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class AuraApplication {

	/**
	 * Zona horaria de negocio. La aplicación trabaja con fechas de España (el JSON ya
	 * va en Europe/Madrid); fijarla aquí evita depender de cómo esté configurado el
	 * reloj del servidor. En un servidor ya en hora de Madrid no cambia nada.
	 */
	static final String ZONA = "Europe/Madrid";

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(ZONA));
		SpringApplication.run(AuraApplication.class, args);
	}
}
