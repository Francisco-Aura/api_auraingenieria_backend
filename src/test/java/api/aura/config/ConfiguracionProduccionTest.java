package api.aura.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.Cookie;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arranca con el application.properties real (el de producción) para comprobar que
 * todas sus claves se interpretan: solo se cambia la conexión a una BD H2 en memoria.
 */
@SpringBootTest
@TestPropertySource(locations = "file:src/main/resources/application.properties", properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:aura_prod_cfg;MODE=MySQL;NON_KEYWORDS=USER,VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.hikari.connection-test-query=SELECT 1"
})
class ConfiguracionProduccionTest {

    @Autowired private ServerProperties server;

    @Test
    void la_cookie_y_la_sesion_quedan_configuradas() {
        var sesion = server.getServlet().getSession();
        Cookie cookie = sesion.getCookie();
        assertThat(cookie.getName()).isEqualTo("AURA_SESSION");
        assertThat(cookie.getHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo(Cookie.SameSite.STRICT);
        assertThat(cookie.getSecure()).isFalse();
        assertThat(sesion.getTimeout()).isEqualTo(Duration.ofDays(30));
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(30));
        assertThat(sesion.isPersistent()).isTrue();
        assertThat(server.getForwardHeadersStrategy()).isEqualTo(ServerProperties.ForwardHeadersStrategy.NATIVE);
        // X-Forwarded-For solo se cree si llega desde el propio equipo (nginx local)
        assertThat(server.getAddress().getHostAddress()).isEqualTo("127.0.0.1");
        String proxies = server.getTomcat().getRemoteip().getInternalProxies();
        assertThat("127.0.0.1").matches(proxies);
        assertThat("192.168.1.50").doesNotMatch(proxies);
        assertThat("10.0.0.7").doesNotMatch(proxies);
    }
}
