package api.aura.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Cada línea del log de una petición lleva su código y el usuario, para poder seguirla. */
class CodigoPeticionLogTest {

    private final AuraUserFilter filtro = new AuraUserFilter();

    private String codigoDurante(MockHttpServletRequest peticion) throws Exception {
        List<String> visto = new ArrayList<>();
        filtro.doFilter(peticion, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) {
                visto.add(MDC.get(AuraUserFilter.MDC_PETICION));
            }
        });
        return visto.get(0);
    }

    @Test
    void con_sesion_el_codigo_lleva_el_usuario_y_al_acabar_se_quita() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/clientes");
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(UsuarioSesion.ATRIBUTO, new UsuarioSesion(7, "Ana", 1));
        peticion.setSession(sesion);

        assertThat(codigoDurante(peticion)).matches("[0-9a-f]{6} u7");
        assertThat(MDC.get(AuraUserFilter.MDC_PETICION)).isNull();
    }

    @Test
    void sin_sesion_solo_el_codigo_y_cada_peticion_el_suyo() throws Exception {
        String primero = codigoDurante(new MockHttpServletRequest("POST", "/api/auth/login"));
        assertThat(primero).matches("[0-9a-f]{6}");
        List<String> otros = new ArrayList<>();
        for (int i = 0; i < 5; i++) otros.add(codigoDurante(new MockHttpServletRequest("GET", "/api/x")));
        assertThat(otros).anyMatch(c -> !c.equals(primero));
    }

    @Test
    void el_formato_del_log_termina_en_espacio_para_no_pegarse_al_nombre_de_la_clase() throws Exception {
        var fuentes = new PropertiesPropertySourceLoader().load("app", new FileSystemResource("src/main/resources/application.properties"));
        StandardEnvironment entorno = new StandardEnvironment();
        fuentes.forEach(entorno.getPropertySources()::addLast);
        assertThat(entorno.getProperty("logging.pattern.correlation")).isEqualTo("[%X{peticion:--}] ");
    }
}
