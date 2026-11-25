package api.aura.controller;

import api.aura.config.AuraUserHolder;
import api.aura.config.UsuarioSesion;
import api.aura.controller.modulo_tareas.TareaTiempoController;
import api.aura.service.modulo_tareas.TareaTiempoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** El tiempo se controla siempre con la identidad de la sesión; el resumen de otro usuario es cosa de administradores. */
class TareaTiempoPermisosTest {

    private final TareaTiempoService servicio = mock(TareaTiempoService.class);
    private final TareaTiempoController controlador = new TareaTiempoController(servicio);
    private final LocalDate hoy = LocalDate.now();

    @AfterEach
    void limpiar() {
        AuraUserHolder.clear();
    }

    @Test
    void sin_sesion_nada_funciona() {
        assertThatThrownBy(() -> controlador.iniciar(1L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(controlador::pausar).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(controlador::reanudar).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(controlador::activo).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controlador.resumen(null, hoy, hoy)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void las_acciones_usan_el_usuario_de_la_sesion() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));

        controlador.iniciar(10L);
        controlador.pausar();

        verify(servicio).iniciar(10L, 7);
        verify(servicio).pausar(7);
    }

    @Test
    void un_usuario_ve_su_resumen_pero_no_el_de_otro() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));

        assertThatCode(() -> controlador.resumen(null, hoy, hoy)).doesNotThrowAnyException();
        assertThatCode(() -> controlador.resumen(7, hoy, hoy)).doesNotThrowAnyException();
        assertThatThrownBy(() -> controlador.resumen(8, hoy, hoy)).isInstanceOf(SecurityException.class);
    }

    @Test
    void un_administrador_ve_el_resumen_de_cualquiera() {
        AuraUserHolder.set(new UsuarioSesion(1, "Admin", 1));

        assertThatCode(() -> controlador.resumen(8, hoy, hoy)).doesNotThrowAnyException();
        verify(servicio).resumen(8, hoy, hoy);
    }
}
