package api.aura.controller;

import api.aura.config.GlobalExceptionHandler;
import api.aura.config.UsuarioSesion;
import api.aura.entity.Usuario;
import api.aura.service.LoginIntentosService;
import api.aura.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El alta de usuarios en Administración solo exige el nombre: un usuario sin
 * email (o sin nivel) tiene que poder entrar igual que el resto.
 */
class UsuarioLoginTest {

    private static MockMvc mvc(UsuarioService servicio) {
        return MockMvcBuilders.standaloneSetup(new UsuarioController(servicio, new LoginIntentosService()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String user, String pass) {
        return post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"user\":\"" + user + "\",\"pass\":\"" + pass + "\"}");
    }

    @Test
    void un_usuario_sin_email_puede_iniciar_sesion() throws Exception {
        UsuarioService servicio = mock(UsuarioService.class);
        Usuario usuario = Usuario.builder().id(3).nombre("Ana Pérez").user("ana").pass("clave").nivelAcceso(2).build();
        when(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "clave")).thenReturn(Optional.of(usuario));

        mvc(servicio).perform(login("ana", "clave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.nivelAcceso").value(2))
                .andExpect(jsonPath("$.email").value(nullValue()));
    }

    @Test
    void el_login_devuelve_el_grupo_de_trabajo_solo_si_lo_tiene() throws Exception {
        UsuarioService servicio = mock(UsuarioService.class);
        Usuario ingeniera = Usuario.builder().id(4).nombre("Eva").user("eva").pass("clave").nivelAcceso(2).grupo("ingenieria").build();
        Usuario sinGrupo = Usuario.builder().id(5).nombre("Luis").user("luis").pass("clave").nivelAcceso(2).build();
        when(servicio.obtenerUsuarioPorNombreUsuarioPass("eva", "clave")).thenReturn(Optional.of(ingeniera));
        when(servicio.obtenerUsuarioPorNombreUsuarioPass("luis", "clave")).thenReturn(Optional.of(sinGrupo));

        mvc(servicio).perform(login("eva", "clave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupo").value("ingenieria"));
        mvc(servicio).perform(login("luis", "clave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupo").doesNotExist());
    }

    @Test
    void credenciales_incorrectas_siguen_devolviendo_401() throws Exception {
        UsuarioService servicio = mock(UsuarioService.class);
        when(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "mala")).thenReturn(Optional.empty());

        mvc(servicio).perform(login("ana", "mala"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void el_login_por_post_abre_una_sesion_nueva_con_el_usuario() throws Exception {
        UsuarioService servicio = mock(UsuarioService.class);
        Usuario usuario = Usuario.builder().id(3).nombre("Ana Pérez").user("ana").pass("clave").nivelAcceso(2).build();
        when(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "clave")).thenReturn(Optional.of(usuario));
        MockHttpSession previa = new MockHttpSession();

        MvcResult resultado = mvc(servicio).perform(post("/api/usuarios/login").session(previa)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"user\":\"ana\",\"pass\":\"clave\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.pass").doesNotExist())
                .andReturn();

        // Session fixation: la sesión anterior al login queda invalidada
        assertThat(previa.isInvalid()).isTrue();
        Object enSesion = resultado.getRequest().getSession(false).getAttribute(UsuarioSesion.ATRIBUTO);
        assertThat(enSesion).isEqualTo(new UsuarioSesion(3, "Ana Pérez", 2));
    }

    @Test
    void tras_diez_fallos_seguidos_el_usuario_queda_bloqueado_aunque_acierte() throws Exception {
        UsuarioService servicio = mock(UsuarioService.class);
        when(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "mala")).thenReturn(Optional.empty());
        when(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "clave"))
                .thenReturn(Optional.of(Usuario.builder().id(3).nombre("Ana").nivelAcceso(2).build()));
        MockMvc mvc = mvc(servicio);

        for (int i = 0; i < 10; i++)
            mvc.perform(login("ana", "mala"))
                    .andExpect(status().isUnauthorized());

        mvc.perform(login("ANA", "clave"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void sin_sesion_de_administrador_no_se_pueden_crear_ni_cambiar_usuarios() throws Exception {
        UsuarioService servicio = mock(UsuarioService.class);
        when(servicio.obtenerUsuarioPorNombreUsuarioPass(anyString(), anyString())).thenReturn(Optional.empty());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new UsuarioController(servicio, new LoginIntentosService()))
                .setControllerAdvice(new api.aura.controller.McasSecurityExceptionHandler(), new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"X\",\"nivelAcceso\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void el_logout_invalida_la_sesion() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(UsuarioSesion.ATRIBUTO, new UsuarioSesion(3, "Ana", 2));

        mvc(mock(UsuarioService.class)).perform(post("/api/usuarios/logout").session(sesion))
                .andExpect(status().isNoContent());

        assertThat(sesion.isInvalid()).isTrue();
    }
}
