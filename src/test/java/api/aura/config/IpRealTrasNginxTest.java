package api.aura.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Con un Tomcat de verdad y la cabecera tal como la manda hoy el nginx de Aura
 * (proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for): la IP que cuenta es
 * la que añade nginx al final; lo que el navegador meta delante se ignora. Así el
 * límite de fallos de login por IP funciona sin cambiar nada en nginx.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "server.forward-headers-strategy=native",
        "server.tomcat.remoteip.internal-proxies=127\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+|0:0:0:0:0:0:0:1|::1"
})
class IpRealTrasNginxTest {

    @Autowired private TestRestTemplate http;

    private int login(String usuario, String xForwardedFor) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Requested-With", "XMLHttpRequest");
        h.set("X-Forwarded-For", xForwardedFor);
        return http.postForEntity("/api/usuarios/login",
                new HttpEntity<>("{\"user\":\"" + usuario + "\",\"pass\":\"x\"}", h), String.class).getStatusCode().value();
    }

    @Test
    void la_ip_buena_es_la_que_anade_nginx_aunque_el_navegador_falsee_otra() {
        // 20 intentos desde el equipo 10.1.2.3 (usuarios distintos), cada uno con una IP
        // falsa distinta delante
        for (int i = 0; i < 20; i++) assertThat(login("prueba" + i, "6.6.6." + i + ", 10.1.2.3")).isEqualTo(401);
        // El 21 se corta: falsear la parte de delante no sirve para saltarse el límite
        assertThat(login("otro", "9.9.9.9, 10.1.2.3")).isEqualTo(429);
        // Otro equipo sigue pudiendo entrar
        assertThat(login("otro", "9.9.9.9, 10.1.2.4")).isEqualTo(401);
    }
}
