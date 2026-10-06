package api.aura.controller;

import api.aura.config.GlobalExceptionHandler;
import api.aura.entity.Usuario;
import api.aura.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El alta de usuarios en Administración solo exige el nombre: un usuario sin
 * email (o sin nivel) tiene que poder entrar igual que el resto.
 */
class UsuarioLoginTest {

    @Test
    void un_usuario_sin_email_puede_iniciar_sesion() throws Exception {
        UsuarioService servicio = mock(UsuarioService.class);
        Usuario usuario = Usuario.builder().id(3).nombre("Ana Pérez").user("ana").pass("clave").nivelAcceso(2).build();
        when(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "clave")).thenReturn(Optional.of(usuario));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new UsuarioController(servicio))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/api/usuarios/login").param("user", "ana").param("pass", "clave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.nivelAcceso").value(2))
                .andExpect(jsonPath("$.email").value(nullValue()));
    }

    @Test
    void credenciales_incorrectas_siguen_devolviendo_401() throws Exception {
        UsuarioService servicio = mock(UsuarioService.class);
        when(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "mala")).thenReturn(Optional.empty());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new UsuarioController(servicio)).build();

        mvc.perform(get("/api/usuarios/login").param("user", "ana").param("pass", "mala"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Usuario o contraseña incorrectos"));
    }
}
