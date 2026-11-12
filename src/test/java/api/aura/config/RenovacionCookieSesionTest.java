package api.aura.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** La cookie de sesión se renueva con el uso: solo caduca tras 30 días sin entrar. */
class RenovacionCookieSesionTest {

    private final RenovacionCookieSesion filtro =
            new RenovacionCookieSesion("AURA_SESSION", Duration.ofDays(30), false, "strict");

    private MockHttpServletResponse pasar(MockHttpSession sesion, String uri) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", uri);
        if (sesion != null) peticion.setSession(sesion);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, new MockFilterChain());
        return respuesta;
    }

    private static MockHttpSession sesionConUsuario() {
        MockHttpSession sesion = new MockHttpSession(null, "ABC123");
        sesion.setAttribute(UsuarioSesion.ATRIBUTO, new UsuarioSesion(7, "Ana", 2));
        return sesion;
    }

    @Test
    void con_la_sesion_en_uso_la_cookie_vuelve_a_durar_30_dias_una_vez_al_dia() throws Exception {
        MockHttpSession sesion = sesionConUsuario();

        String cookie = pasar(sesion, "/api/clientes").getHeader("Set-Cookie");
        assertThat(cookie).startsWith("AURA_SESSION=ABC123").contains("Max-Age=2592000")
                .contains("Path=/").contains("HttpOnly").contains("SameSite=Strict").doesNotContain("Secure");

        assertThat(pasar(sesion, "/api/clientes").getHeader("Set-Cookie")).as("el mismo día, no").isNull();

        sesion.setAttribute(RenovacionCookieSesion.ATRIBUTO, System.currentTimeMillis() - RenovacionCookieSesion.CADA_MS);
        assertThat(pasar(sesion, "/api/clientes").getHeader("Set-Cookie")).as("al día siguiente, sí").isNotNull();
    }

    @Test
    void sin_sesion_sin_usuario_o_al_cerrar_sesion_no_se_toca() throws Exception {
        assertThat(pasar(null, "/api/clientes").getHeader("Set-Cookie")).isNull();
        assertThat(pasar(new MockHttpSession(), "/api/clientes").getHeader("Set-Cookie")).isNull();
        assertThat(pasar(sesionConUsuario(), "/api/usuarios/logout").getHeader("Set-Cookie")).isNull();
    }

    @Test
    void sin_caducidad_configurada_no_hace_nada() throws Exception {
        RenovacionCookieSesion sinCaducidad = new RenovacionCookieSesion("AURA_SESSION", null, false, "");
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/clientes");
        peticion.setSession(sesionConUsuario());
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        sinCaducidad.doFilter(peticion, respuesta, new MockFilterChain());
        assertThat(respuesta.getHeader("Set-Cookie")).isNull();
    }
}
