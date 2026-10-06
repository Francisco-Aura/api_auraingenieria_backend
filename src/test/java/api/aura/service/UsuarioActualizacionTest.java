package api.aura.service;

import api.aura.entity.Usuario;
import api.aura.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Actualizar un usuario copiaba la contraseña recibida tal cual. Un cliente que no
 * mandara ese campo (o lo mandara vacío) dejaba al usuario sin contraseña y sin
 * poder entrar, sin ningún aviso.
 */
class UsuarioActualizacionTest {

    private final UsuarioRepository repositorio = mock(UsuarioRepository.class);
    private final UsuarioService servicio = new UsuarioService();

    private Usuario enBaseDeDatos() {
        Usuario usuario = Usuario.builder().id(4).nombre("ANA COMERCIAL").user("ana")
                .pass("secreto").email("ana@aura.es").nivelAcceso(1).build();
        ReflectionTestUtils.setField(servicio, "usuarioRepository", repositorio);
        when(repositorio.findById(4)).thenReturn(Optional.of(usuario));
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return usuario;
    }

    @Test
    void sin_contrasena_en_la_peticion_se_conserva_la_que_ya_tenia() {
        Usuario existente = enBaseDeDatos();

        Usuario resultado = servicio.actualizarUsuario(4,
                Usuario.builder().nombre("ANA C.").user("ana").nivelAcceso(1).build());

        assertThat(resultado.getPass()).isEqualTo("secreto");
        assertThat(existente.getNombre()).isEqualTo("ANA C.");
    }

    @Test
    void con_la_contrasena_en_blanco_tampoco_se_borra() {
        enBaseDeDatos();

        Usuario resultado = servicio.actualizarUsuario(4,
                Usuario.builder().nombre("ANA COMERCIAL").user("ana").pass("   ").nivelAcceso(1).build());

        assertThat(resultado.getPass()).isEqualTo("secreto");
    }

    @Test
    void con_una_contrasena_nueva_se_cambia() {
        enBaseDeDatos();

        Usuario resultado = servicio.actualizarUsuario(4,
                Usuario.builder().nombre("ANA COMERCIAL").user("ana").pass("otra").nivelAcceso(1).build());

        assertThat(resultado.getPass()).isEqualTo("otra");
    }
}
