package api.aura.service;

import api.aura.entity.Usuario;
import api.aura.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Contraseñas: cifrado reversible, migración de las guardadas en claro y comparación como MariaDB. */
class UsuarioContrasenaTest {

    private static final CifradoContrasenas CIFRADO = UsuarioActualizacionTest.CIFRADO;
    private final UsuarioRepository repositorio = mock(UsuarioRepository.class);
    private final UsuarioService servicio = new UsuarioService();

    UsuarioContrasenaTest() {
        ReflectionTestUtils.setField(servicio, "usuarioRepository", repositorio);
        ReflectionTestUtils.setField(servicio, "cifrado", CIFRADO);
        when(repositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void una_contrasena_en_claro_se_acepta_y_se_guarda_cifrada_tal_cual_era() {
        Usuario ana = Usuario.builder().id(1).user("ana").pass("Aura2022 ").build();
        when(repositorio.findAllByUser("ana")).thenReturn(List.of(ana));

        assertThat(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "aura2022")).contains(ana);

        assertThat(ana.getPass()).startsWith("{c}");
        // El administrador la sigue viendo exactamente como estaba
        assertThat(servicio.contrasenaVisible(ana)).isEqualTo("Aura2022 ");
        assertThat(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "AURA2022")).contains(ana);
    }

    @Test
    void se_compara_como_mariadb_sin_mayusculas_ni_tildes_y_la_enye_cuenta() {
        Usuario ana = Usuario.builder().id(1).user("ana").pass(CIFRADO.cifrar("Muñeca")).build();
        when(repositorio.findAllByUser("ana")).thenReturn(List.of(ana));

        assertThat(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "MUÑECA ")).contains(ana);
        assertThat(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "muñéca")).contains(ana);
        assertThat(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "muneca")).isEmpty();
        assertThat(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "otra")).isEmpty();
        verify(repositorio, never()).save(any());
    }

    @Test
    void un_hash_de_una_version_previa_se_acepta_y_pasa_a_cifrado() {
        Usuario ana = Usuario.builder().id(1).user("ana").pass(new BCryptPasswordEncoder().encode("secreto")).build();
        when(repositorio.findAllByUser("ana")).thenReturn(List.of(ana));

        assertThat(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "Secreto")).contains(ana);
        assertThat(servicio.contrasenaVisible(ana)).isEqualTo("Secreto");
    }

    @Test
    void contrasena_vacia_o_usuario_sin_contrasena_no_entran() {
        Usuario sinPass = Usuario.builder().id(2).user("bea").build();
        when(repositorio.findAllByUser("bea")).thenReturn(List.of(sinPass));

        assertThat(servicio.obtenerUsuarioPorNombreUsuarioPass("bea", "")).isEmpty();
        assertThat(servicio.obtenerUsuarioPorNombreUsuarioPass("bea", "   ")).isEmpty();
        assertThat(servicio.obtenerUsuarioPorNombreUsuarioPass("bea", "x")).isEmpty();
        assertThat(servicio.obtenerUsuarioPorNombreUsuarioPass("nadie", "x")).isEmpty();
    }

    @Test
    void el_alta_guarda_cifrada_la_contrasena_e_ignora_el_id_recibido() {
        Usuario nuevo = servicio.crearUsuario(Usuario.builder().id(99).nombre("N").user("n").pass("Clave").build());

        assertThat(nuevo.getId()).isNull();
        assertThat(nuevo.getPass()).startsWith("{c}");
        assertThat(servicio.contrasenaVisible(nuevo)).isEqualTo("Clave");
    }

    @Test
    void una_contrasena_larga_que_empieza_por_el_prefijo_de_cifrado_se_rechaza() {
        // Guardada sin cifrar se tomaría por cifrada y su usuario no podría entrar
        String rara = "{c}" + "x".repeat(60);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                servicio.crearUsuario(Usuario.builder().nombre("N").user("n").pass(rara).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("{c}");

        // Corta, se cifra y se lee bien: no hay por qué rechazarla
        Usuario corta = servicio.crearUsuario(Usuario.builder().nombre("N").user("n").pass("{c}abc").build());
        assertThat(servicio.contrasenaVisible(corta)).isEqualTo("{c}abc");
    }

    @Test
    void una_contrasena_que_no_cabe_cifrada_se_guarda_como_antes_y_se_sigue_viendo() {
        // Antes se aceptaban contraseñas de hasta 100 caracteres: no se rechazan
        String larga = "x".repeat(60);
        Usuario nuevo = servicio.crearUsuario(Usuario.builder().nombre("N").user("n").pass(larga).build());

        assertThat(nuevo.getPass()).isEqualTo(larga);
        assertThat(servicio.contrasenaVisible(nuevo)).isEqualTo(larga);
    }

    @Test
    void al_arrancar_se_cifran_las_que_siguen_en_claro_y_se_puede_volver_atras() {
        Usuario claro = Usuario.builder().id(1).user("a").pass("uno").build();
        Usuario cifrada = Usuario.builder().id(2).user("b").pass(CIFRADO.cifrar("dos")).build();
        Usuario sinPass = Usuario.builder().id(3).user("c").build();
        when(repositorio.findAll()).thenReturn(List.of(claro, cifrada, sinPass));

        assertThat(servicio.cifrarPendientes()).isEqualTo(1);
        assertThat(claro.getPass()).startsWith("{c}");
        assertThat(sinPass.getPass()).isNull();

        assertThat(servicio.descifrarTodas()).isEqualTo(2);
        assertThat(claro.getPass()).isEqualTo("uno");
        assertThat(cifrada.getPass()).isEqualTo("dos");
    }

    @Test
    void en_modo_vuelta_atras_no_se_cifra_nada_nuevo() {
        ReflectionTestUtils.setField(servicio, "modoContrasenas", "descifrar");
        Usuario ana = Usuario.builder().id(1).user("ana").pass("Clave").build();
        when(repositorio.findAllByUser("ana")).thenReturn(List.of(ana));

        // Entra, pero su contraseña sigue en claro para que la versión anterior la lea
        assertThat(servicio.obtenerUsuarioPorNombreUsuarioPass("ana", "clave")).contains(ana);
        assertThat(ana.getPass()).isEqualTo("Clave");
        assertThat(servicio.crearUsuario(Usuario.builder().nombre("N").user("n").pass("Nueva").build()).getPass())
                .isEqualTo("Nueva");
    }
}
