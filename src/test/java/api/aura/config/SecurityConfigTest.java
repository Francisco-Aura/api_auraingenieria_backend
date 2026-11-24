package api.aura.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La cadena de seguridad real: sesión obligatoria, cabecera AJAX y cabeceras de seguridad. */
@SpringBootTest
class SecurityConfigTest {

    @Autowired private WebApplicationContext contexto;
    @Autowired private api.aura.repository.UsuarioRepository usuarios;
    private MockMvc mvc;
    private final UsuarioSesion usuario = new UsuarioSesion(1, "Usuario prueba", 2);

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(new AuraUserFilter())
                .apply(springSecurity())
                .build();
    }

    @Test
    void sin_sesion_la_api_responde_401_aunque_se_envie_la_cabecera_de_usuario() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
        mvc.perform(get("/api/usuarios").header("X-Aura-Usuario-Nombre", "Administrador"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void con_sesion_se_accede_y_no_se_devuelven_contrasenas() throws Exception {
        mvc.perform(get("/api/usuarios").sessionAttr(UsuarioSesion.ATRIBUTO, usuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].pass").doesNotExist())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    void el_login_es_publico_pero_exige_la_cabecera_ajax_en_post() throws Exception {
        mvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\":\"nadie\",\"pass\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios/login").header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"user\":\"nadie\",\"pass\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void el_modulo_planificador_tambien_exige_la_sesion_de_la_intranet_y_la_cabecera_ajax_para_guardar() throws Exception {
        // Su página (/planificador/) es aparte, pero la API es de este backend: mismas reglas
        mvc.perform(get("/api/planificador/health")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/planificador/health").header("X-Usuario-Id", "1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/planificador/health").sessionAttr(UsuarioSesion.ATRIBUTO, usuario))
                .andExpect(status().isOk());
        mvc.perform(post("/api/planificador/suppliers").sessionAttr(UsuarioSesion.ATRIBUTO, usuario)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Proveedor de prueba\"}"))
                .andExpect(status().isForbidden());
        // Con la cabecera que envía su pantalla (src/planificador/main.js) la petición llega al módulo
        int estado = mvc.perform(post("/api/planificador/suppliers").sessionAttr(UsuarioSesion.ATRIBUTO, usuario)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Proveedor de prueba\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(estado).isNotIn(401, 403);
    }

    @Test
    void una_modificacion_sin_cabecera_ajax_se_rechaza_aun_con_sesion() throws Exception {
        mvc.perform(post("/api/usuarios/logout").sessionAttr(UsuarioSesion.ATRIBUTO, usuario))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios/logout").sessionAttr(UsuarioSesion.ATRIBUTO, usuario)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent());
    }

    @Test
    void la_cabecera_de_usuario_del_frontend_no_sirve_ni_de_identidad_ni_de_prueba_ajax() throws Exception {
        mvc.perform(post("/api/usuarios/logout").sessionAttr(UsuarioSesion.ATRIBUTO, usuario)
                        .header("X-Aura-Usuario-Nombre", "Usuario prueba"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").header("X-Aura-Usuario-Nombre", "Administrador")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void el_login_por_get_ya_no_existe() throws Exception {
        mvc.perform(get("/api/usuarios/login").param("user", "nadie").param("pass", "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void un_administrador_ve_las_contrasenas_en_claro_y_en_la_bd_estan_cifradas() throws Exception {
        UsuarioSesion admin = new UsuarioSesion(1, "Admin prueba", 1);
        mvc.perform(post("/api/usuarios").sessionAttr(UsuarioSesion.ATRIBUTO, admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Con clave\",\"user\":\"conclave\",\"pass\":\"Clave123\",\"nivelAcceso\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pass").value("Clave123"));

        mvc.perform(get("/api/usuarios").sessionAttr(UsuarioSesion.ATRIBUTO, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.user == 'conclave')].pass").value(org.hamcrest.Matchers.contains("Clave123")));
        // Un usuario normal no las ve, ni tampoco el usuario del login de los demás
        mvc.perform(get("/api/usuarios").sessionAttr(UsuarioSesion.ATRIBUTO, usuario))
                .andExpect(jsonPath("$[?(@.nombre == 'Con clave')]").isNotEmpty())
                .andExpect(jsonPath("$[*].pass").doesNotExist())
                .andExpect(jsonPath("$[*].user").doesNotExist());
        // En la BD no está en claro
        assertThat(usuarios.findAllByUser("conclave")).allSatisfy(u -> assertThat(u.getPass()).startsWith("{c}").doesNotContain("Clave123"));
    }

    @Test
    void un_usuario_que_no_es_administrador_no_puede_crear_usuarios() throws Exception {
        mvc.perform(post("/api/usuarios").sessionAttr(UsuarioSesion.ATRIBUTO, usuario)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Intruso\",\"nivelAcceso\":1}"))
                .andExpect(status().isForbidden());
    }
}
