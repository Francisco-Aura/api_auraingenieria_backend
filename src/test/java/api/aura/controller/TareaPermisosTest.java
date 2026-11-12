package api.aura.controller;

import api.aura.config.AuraUserHolder;
import api.aura.config.UsuarioSesion;
import api.aura.controller.modulo_tareas.TareaController;
import api.aura.dto.modulo_tareas.CambioEstadoRequest;
import api.aura.dto.modulo_tareas.TareaDTO;
import api.aura.repository.modulo_tareas.AdjuntoRepository;
import api.aura.service.modulo_tareas.FileStorageService;
import api.aura.service.modulo_tareas.TareaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Igual que la pantalla de Tareas: una tarea creada por un administrador solo la editan o eliminan administradores. */
class TareaPermisosTest {

    private final TareaService servicio = mock(TareaService.class);
    private final TareaController controlador = new TareaController(servicio,
            mock(FileStorageService.class), mock(AdjuntoRepository.class));

    /** Por defecto, tareas que el usuario ve en pantalla; cada prueba marca las que no. */
    @BeforeEach
    void tareasVisibles() {
        when(servicio.puedeVerTarea(any(), any())).thenReturn(true);
    }

    @AfterEach
    void limpiar() {
        AuraUserHolder.clear();
    }

    @Test
    void un_usuario_no_edita_ni_elimina_la_tarea_de_un_administrador() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));
        when(servicio.creadaPorAdministrador(10L)).thenReturn(true);

        assertThatThrownBy(() -> controlador.actualizar(10L, new TareaDTO())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> controlador.eliminar(10L)).isInstanceOf(SecurityException.class);
        verify(servicio, never()).eliminar(any());
        verify(servicio, never()).actualizar(any(), any());
    }

    @Test
    void sus_propias_tareas_y_las_de_otros_usuarios_si() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));
        when(servicio.creadaPorAdministrador(11L)).thenReturn(false);

        assertThatCode(() -> controlador.eliminar(11L)).doesNotThrowAnyException();
        verify(servicio).eliminar(11L);
    }

    @Test
    void un_administrador_puede_con_todas() {
        AuraUserHolder.set(new UsuarioSesion(1, "Admin", 1));
        when(servicio.creadaPorAdministrador(10L)).thenReturn(true);

        assertThatCode(() -> controlador.eliminar(10L)).doesNotThrowAnyException();
    }

    @Test
    void un_usuario_no_lista_las_tareas_de_todos_ni_las_de_otro() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));

        assertThatThrownBy(controlador::obtenerTodas).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> controlador.obtenerPorUsuario(8)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> controlador.contarActivas(8)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> controlador.estadisticas(8, 1)).isInstanceOf(SecurityException.class);
        assertThatCode(() -> controlador.obtenerPorUsuario(7)).doesNotThrowAnyException();
        verify(servicio, never()).obtenerTodas();
    }

    @Test
    void vencen_hoy_y_estadisticas_usan_el_usuario_y_el_nivel_de_la_sesion() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));

        controlador.vencenHoy(1, 1);
        controlador.estadisticas(7, 1);

        verify(servicio).obtenerVencenHoy(7, 2);
        verify(servicio).obtenerEstadisticas(7, 2);
    }

    @Test
    void el_cambio_de_estado_queda_a_nombre_de_quien_lo_hace() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));

        controlador.cambiarEstado(10L, 3L);

        ArgumentCaptor<CambioEstadoRequest> peticion = ArgumentCaptor.forClass(CambioEstadoRequest.class);
        verify(servicio).cambiarEstado(eq(10L), peticion.capture());
        assertThat(peticion.getValue().getEstadoId()).isEqualTo(3L);
        assertThat(peticion.getValue().getUsuarioAccionId()).isEqualTo(7);
    }

    @Test
    void un_usuario_no_sube_adjuntos_a_la_tarea_de_un_administrador() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));
        when(servicio.creadaPorAdministrador(10L)).thenReturn(true);

        assertThatThrownBy(() -> controlador.subirAdjunto(10L, null)).isInstanceOf(SecurityException.class);
    }

    @Test
    void una_tarea_que_no_ve_en_pantalla_tampoco_se_lee_por_su_numero() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));
        when(servicio.puedeVerTarea(20L, 7)).thenReturn(false);
        api.aura.service.modulo_tareas.TareaComentarioService comentarios =
                mock(api.aura.service.modulo_tareas.TareaComentarioService.class);
        when(comentarios.tareaDelAdjunto(5L)).thenReturn(20L);
        when(comentarios.obtenerAdjunto(5L)).thenReturn(api.aura.entity.modulo_tareas.mt_Adjunto.builder().id(5L).build());
        var comentariosControlador = new api.aura.controller.modulo_tareas.TareaComentarioController(
                comentarios, mock(FileStorageService.class), servicio);

        assertThatThrownBy(() -> controlador.obtenerPorId(20L)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> controlador.historial(20L)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> controlador.obtenerAdjuntos(20L)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> comentariosControlador.obtenerPorTarea(20L)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> comentariosControlador.descargarAdjunto(5L)).isInstanceOf(SecurityException.class);
        verify(servicio, never()).obtenerPorId(any());
        verify(comentarios, never()).obtenerPorTarea(any());
    }

    @Test
    void la_que_si_ve_se_lee_y_un_administrador_lee_todas() {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));
        when(servicio.puedeVerTarea(21L, 7)).thenReturn(true);
        assertThatCode(() -> controlador.obtenerPorId(21L)).doesNotThrowAnyException();

        AuraUserHolder.set(new UsuarioSesion(1, "Admin", 1));
        assertThatCode(() -> controlador.obtenerPorId(20L)).doesNotThrowAnyException();
        verify(servicio, never()).puedeVerTarea(eq(20L), any());
    }

    @Test
    void una_tarea_que_no_ve_en_pantalla_tampoco_se_cambia_ni_se_comenta() throws Exception {
        AuraUserHolder.set(new UsuarioSesion(7, "Ana", 2));
        when(servicio.puedeVerTarea(20L, 7)).thenReturn(false);
        api.aura.service.modulo_tareas.TareaComentarioService comentarios =
                mock(api.aura.service.modulo_tareas.TareaComentarioService.class);
        when(comentarios.tareaDelComentario(4L)).thenReturn(20L);
        when(comentarios.tareaDelAdjunto(5L)).thenReturn(20L);
        var comentariosControlador = new api.aura.controller.modulo_tareas.TareaComentarioController(
                comentarios, mock(FileStorageService.class), servicio);
        CambioEstadoRequest estado = new CambioEstadoRequest();
        estado.setEstadoId(3L);
        api.aura.dto.modulo_tareas.ComentarioDTO comentario = new api.aura.dto.modulo_tareas.ComentarioDTO();
        comentario.setTareaId(20L);

        assertThatThrownBy(() -> controlador.actualizar(20L, new TareaDTO())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> controlador.eliminar(20L)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> controlador.cambiarEstado(20L, 3L)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> controlador.cambiarEstadoAvanzado(20L, estado)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> controlador.toggleFijada(20L, java.util.Map.of("fijada", true))).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> controlador.subirAdjunto(20L, null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> comentariosControlador.crear(comentario)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> comentariosControlador.toggleFijado(4L)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> comentariosControlador.subirAdjunto(4L, null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> comentariosControlador.eliminarAdjunto(5L)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> comentariosControlador.actualizarAdjunto(5L, null)).isInstanceOf(SecurityException.class);

        verify(servicio, never()).actualizar(any(), any());
        verify(servicio, never()).eliminar(any());
        verify(servicio, never()).cambiarEstado(any(), any(CambioEstadoRequest.class));
        verify(servicio, never()).actualizarFijada(any(), any());
        verify(comentarios, never()).crearComentario(any());
        verify(comentarios, never()).toggleFijado(any());
        verify(comentarios, never()).agregarAdjunto(any(), any());
        verify(comentarios, never()).eliminarAdjunto(any());
        verify(comentarios, never()).actualizarAdjunto(any(), any());
    }
}
