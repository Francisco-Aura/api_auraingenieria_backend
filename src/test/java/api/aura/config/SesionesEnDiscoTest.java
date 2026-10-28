package api.aura.config;

import api.aura.AuraApplication;
import api.aura.entity.Usuario;
import api.aura.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un backend nuevo acepta la sesión abierta en el anterior aunque este no se haya
 * parado de forma ordenada (como con un kill -9 al desplegar): el usuario sigue dentro.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "aura.sesiones.en-disco=true",
        "server.tomcat.background-processor-delay=1s"
})
class SesionesEnDiscoTest {

    private static Path carpeta;

    @DynamicPropertySource
    static void carpeta(DynamicPropertyRegistry registro) throws IOException {
        carpeta = Files.createTempDirectory("aura-sesiones");
        registro.add("aura.sesiones.directorio", carpeta::toString);
    }

    @Autowired private TestRestTemplate http;
    @Autowired private UsuarioService usuarios;

    @Test
    void la_sesion_sobrevive_a_un_reinicio_sin_parada_ordenada() throws Exception {
        usuarios.crearUsuario(Usuario.builder().nombre("Ana").user("ana.disco").pass("clave").nivelAcceso(2).build());
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Requested-With", "XMLHttpRequest");
        ResponseEntity<String> login = http.postForEntity("/api/usuarios/login",
                new HttpEntity<>("{\"user\":\"ana.disco\",\"pass\":\"clave\"}", h), String.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        String cookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];

        // Se escribe en disco sin esperar a que el backend se pare
        long limite = System.currentTimeMillis() + 15_000;
        while (ficherosDeSesion() == 0 && System.currentTimeMillis() < limite) Thread.sleep(200);
        assertThat(ficherosDeSesion()).isEqualTo(1);
        assertThat(Files.getPosixFilePermissions(carpeta.resolve("AURA_SESSION")))
                .as("solo el usuario del backend puede leerlas").isEqualTo(PosixFilePermissions.fromString("rwx------"));

        // Otro backend con la misma carpeta (el primero ni se ha parado ni ha guardado nada al salir)
        try (ConfigurableApplicationContext nuevo = new SpringApplicationBuilder(AuraApplication.class).run(
                "--server.port=0",
                "--aura.sesiones.en-disco=true",
                "--aura.sesiones.directorio=" + carpeta,
                // La misma BD, como en un reinicio de verdad (sin recrear las tablas)
                "--spring.jpa.hibernate.ddl-auto=none")) {
            int puerto = ((ServletWebServerApplicationContext) nuevo).getWebServer().getPort();
            String url = "http://localhost:" + puerto + "/api/usuarios";

            HttpHeaders conSesion = new HttpHeaders();
            conSesion.set(HttpHeaders.COOKIE, cookie);
            assertThat(http.exchange(url, HttpMethod.GET, new HttpEntity<>(conSesion), String.class).getStatusCode().value())
                    .as("sigue dentro").isEqualTo(200);
            assertThat(http.getForEntity(url, String.class).getStatusCode().value())
                    .as("sin la cookie, no").isEqualTo(401);

            // Cerrar sesión borra la copia en disco: esa cookie ya no vale en ningún backend
            HttpHeaders salir = new HttpHeaders();
            salir.set(HttpHeaders.COOKIE, cookie);
            salir.set("X-Requested-With", "XMLHttpRequest");
            http.exchange("http://localhost:" + puerto + "/api/usuarios/logout", HttpMethod.POST, new HttpEntity<>(salir), String.class);
            assertThat(ficherosDeSesion()).isZero();
        }
    }

    private static long ficherosDeSesion() throws IOException {
        try (Stream<Path> f = Files.walk(carpeta)) {
            return f.filter(p -> p.toString().endsWith(".session")).count();
        }
    }
}
