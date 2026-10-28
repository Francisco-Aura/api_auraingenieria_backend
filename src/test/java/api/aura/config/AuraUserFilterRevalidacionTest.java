package api.aura.config;

import api.aura.entity.Usuario;
import api.aura.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La sesión dura semanas: el filtro relee el usuario en la BD para recoger bajas y cambios de nivel. */
class AuraUserFilterRevalidacionTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final AuraUserFilter filtro = new AuraUserFilter(usuarios);

    private UsuarioSesion pasarPorElFiltro(MockHttpSession sesion) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/clientes");
        peticion.setSession(sesion);
        AtomicReference<UsuarioSesion> visto = new AtomicReference<>();
        filtro.doFilter(peticion, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) {
                visto.set(AuraUserFilter.usuarioDeSesion((jakarta.servlet.http.HttpServletRequest) r) == null ? null
                        : new UsuarioSesion(AuraUserHolder.getId(), AuraUserHolder.get(), AuraUserHolder.getNivelAcceso()));
            }
        });
        return visto.get();
    }

    @Test
    void si_cambia_la_contrasena_se_cierran_las_sesiones_abiertas_con_la_anterior() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(UsuarioSesion.ATRIBUTO, new UsuarioSesion(6, "Luis", 2));
        sesion.setAttribute(AuraUserFilter.ATRIBUTO_HUELLA, AuraUserFilter.huella("hash-antiguo"));
        when(usuarios.findById(6)).thenReturn(Optional.of(Usuario.builder().id(6).nombre("Luis").nivelAcceso(2).pass("hash-nuevo").build()));

        assertThat(pasarPorElFiltro(sesion)).isNull();
        assertThat(sesion.isInvalid()).isTrue();
    }

    @Test
    void una_sesion_sin_huella_la_adopta_sin_cerrarse() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(UsuarioSesion.ATRIBUTO, new UsuarioSesion(6, "Luis", 2));
        when(usuarios.findById(6)).thenReturn(Optional.of(Usuario.builder().id(6).nombre("Luis").nivelAcceso(2).pass("hash").build()));

        assertThat(pasarPorElFiltro(sesion)).isEqualTo(new UsuarioSesion(6, "Luis", 2));
        assertThat(sesion.getAttribute(AuraUserFilter.ATRIBUTO_HUELLA)).isEqualTo(AuraUserFilter.huella("hash"));
    }

    @Test
    void un_administrador_degradado_deja_de_serlo_y_un_usuario_borrado_pierde_la_sesion() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(UsuarioSesion.ATRIBUTO, new UsuarioSesion(5, "Ana", 1));
        when(usuarios.findById(5)).thenReturn(Optional.of(Usuario.builder().id(5).nombre("Ana ").nivelAcceso(2).build()));

        assertThat(pasarPorElFiltro(sesion)).isEqualTo(new UsuarioSesion(5, "Ana", 2));
        assertThat(sesion.getAttribute(UsuarioSesion.ATRIBUTO)).isEqualTo(new UsuarioSesion(5, "Ana", 2));

        // Dentro del minuto no se vuelve a consultar
        pasarPorElFiltro(sesion);
        verify(usuarios, times(1)).findById(5);

        // Pasado el minuto, si el usuario ya no existe la sesión se cierra
        sesion.setAttribute(AuraUserFilter.ATRIBUTO_VALIDADO, System.currentTimeMillis() - AuraUserFilter.REVALIDAR_CADA_MS - 1);
        when(usuarios.findById(5)).thenReturn(Optional.empty());
        assertThat(pasarPorElFiltro(sesion)).isNull();
        assertThat(sesion.isInvalid()).isTrue();
    }
}
