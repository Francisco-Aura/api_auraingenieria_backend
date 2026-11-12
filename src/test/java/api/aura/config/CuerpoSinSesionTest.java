package api.aura.config;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.FormContentFilter;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sin sesión, el servidor no lee cuerpos enormes: ni en el login ni en un formulario
 * PUT/PATCH/DELETE (FormContentFilter lo lee entero antes de comprobar la sesión).
 */
@SpringBootTest
class CuerpoSinSesionTest {

    @Autowired private WebApplicationContext contexto;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        // Mismo orden que en el servidor: AuraUserFilter y después FormContentFilter
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(new AuraUserFilter(), new FormContentFilter())
                .apply(springSecurity())
                .build();
    }

    @Test
    void un_login_normal_sigue_funcionando() throws Exception {
        mvc.perform(post("/api/usuarios/login").header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"user\":\"nadie\",\"pass\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void un_login_con_un_cuerpo_desmesurado_se_rechaza_sin_cargarlo_entero() throws Exception {
        String enorme = "{\"user\":\"" + "a".repeat(CuerpoAcotado.MAXIMO * 4) + "\",\"pass\":\"x\"}";
        mvc.perform(post("/api/usuarios/login").header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content(enorme))
                .andExpect(status().isBadRequest());
    }

    @Test
    void un_formulario_enorme_sin_sesion_no_se_lee_entero() {
        String enorme = "a=" + "b".repeat(CuerpoAcotado.MAXIMO * 4);
        assertThatThrownBy(() -> mvc.perform(put("/api/clientes/1")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content(enorme)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("sin sesión");
    }

    @Test
    void con_sesion_el_cuerpo_no_se_acota() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("PUT", "/api/clientes/1");
        peticion.getSession(true).setAttribute(UsuarioSesion.ATRIBUTO, new UsuarioSesion(1, "Ana", 2));
        byte[] cuerpo = "x".repeat(CuerpoAcotado.MAXIMO * 2).getBytes(StandardCharsets.UTF_8);
        peticion.setContent(cuerpo);
        int[] leidos = new int[1];
        HttpServlet lector = new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
                leidos[0] = req.getInputStream().readAllBytes().length;
            }
        };

        new AuraUserFilter().doFilter(peticion, new MockHttpServletResponse(), new MockFilterChain(lector));

        assertThat(leidos[0]).isEqualTo(cuerpo.length);
    }

    @Test
    void sin_sesion_se_lee_hasta_el_maximo_y_ni_un_byte_mas() throws Exception {
        MockHttpServletRequest dentro = new MockHttpServletRequest("POST", "/api/usuarios/login");
        dentro.setContent(new byte[CuerpoAcotado.MAXIMO]);
        assertThat(new CuerpoAcotado(dentro).getInputStream().readAllBytes()).hasSize(CuerpoAcotado.MAXIMO);

        MockHttpServletRequest fuera = new MockHttpServletRequest("POST", "/api/usuarios/login");
        fuera.setContent(new byte[CuerpoAcotado.MAXIMO + 1]);
        assertThatThrownBy(() -> new CuerpoAcotado(fuera).getInputStream().readAllBytes())
                .isInstanceOf(IOException.class);
        MockHttpServletRequest texto = new MockHttpServletRequest("POST", "/api/usuarios/login");
        texto.setContent(new byte[CuerpoAcotado.MAXIMO + 1]);
        assertThatThrownBy(() -> new CuerpoAcotado(texto).getReader().transferTo(Writer.nullWriter()))
                .isInstanceOf(IOException.class);
    }
}
