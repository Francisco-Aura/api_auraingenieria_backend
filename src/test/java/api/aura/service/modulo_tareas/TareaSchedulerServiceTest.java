package api.aura.service.modulo_tareas;

import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Notificacion;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.mt_NotificacionRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TareaSchedulerServiceTest {

    private final mt_TareaRepository tareas = mock(mt_TareaRepository.class);
    private final TareaNotificacionService avisos = mock(TareaNotificacionService.class);

    private mt_Tarea tarea(long id, Usuario asignado) {
        // Como las que devuelve la consulta: con aviso activado y vencimiento en la próxima hora
        mt_Tarea tarea = mt_Tarea.builder().id(id).titulo("Tarea " + id).usuarioAsignado(asignado).notificar(true)
                .fechaVencimiento(LocalDateTime.now().plusMinutes(30)).build();
        // Cada tarea se relee en su propia transacción antes de avisar
        when(tareas.findById(id)).thenReturn(Optional.of(tarea));
        return tarea;
    }

    private TareaSchedulerService servicio(mt_NotificacionRepository notificaciones, UsuarioRepository usuarios) {
        return new TareaSchedulerService(tareas, notificaciones, usuarios, avisos, mock(PlatformTransactionManager.class));
    }

    @Test
    void los_administradores_se_consultan_una_vez_por_ejecucion_y_se_notifica_a_todos() {
        mt_NotificacionRepository notificaciones = mock(mt_NotificacionRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario comercial = Usuario.builder().id(5).nombre("Comercial").build();
        Usuario admin = Usuario.builder().id(1).nombre("Admin").nivelAcceso(1).build();
        List<mt_Tarea> proximas = List.of(tarea(1, comercial), tarea(2, comercial), tarea(3, admin));
        when(tareas.findTareasParaNotificarVencimiento(any(), any())).thenReturn(proximas);
        when(usuarios.findByNivelAcceso(1)).thenReturn(List.of(admin));

        servicio(notificaciones, usuarios).verificarTareasProximasAVencer();

        verify(usuarios, times(1)).findByNivelAcceso(1);
        // Tareas 1 y 2: asignado + administrador; tarea 3: el administrador es el asignado.
        verify(notificaciones, times(5)).save(any(mt_Notificacion.class));
        // Y cada aviso sale al navegador al instante (tras confirmar su transacción)
        verify(avisos, times(5)).enviarTrasConfirmar(any(mt_Notificacion.class));
    }

    @Test
    void notificar_1h_antes_solo_busca_las_tareas_que_vencen_en_la_proxima_hora() {
        mt_NotificacionRepository notificaciones = mock(mt_NotificacionRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario comercial = Usuario.builder().id(5).nombre("Comercial").build();
        mt_Tarea proxima = tarea(1, comercial);
        when(tareas.findTareasParaNotificarVencimiento(any(), any())).thenReturn(List.of(proxima));

        servicio(notificaciones, usuarios).verificarTareasProximasAVencer();

        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> hasta = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tareas).findTareasParaNotificarVencimiento(desde.capture(), hasta.capture());
        assertThat(Duration.between(desde.getValue(), hasta.getValue())).isEqualTo(Duration.ofHours(1));
        ArgumentCaptor<mt_Notificacion> aviso = ArgumentCaptor.forClass(mt_Notificacion.class);
        verify(notificaciones).save(aviso.capture());
        assertThat(aviso.getValue().getMensaje()).contains("vence a las").contains("en menos de 1 hora");
    }

    @Test
    void un_fallo_con_una_tarea_no_impide_avisar_y_marcar_las_demas() {
        mt_NotificacionRepository notificaciones = mock(mt_NotificacionRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario comercial = Usuario.builder().id(5).nombre("Comercial").build();
        mt_Tarea falla = tarea(1, comercial);
        mt_Tarea buena = tarea(2, comercial);
        when(tareas.findTareasParaNotificarVencimiento(any(), any())).thenReturn(List.of(falla, buena));
        doThrow(new IllegalStateException("BD caída")).when(notificaciones)
                .save(org.mockito.ArgumentMatchers.argThat(n -> n != null && n.getTarea() == falla));

        servicio(notificaciones, usuarios).verificarTareasProximasAVencer();

        // La tarea que falla se deshace en su propia transacción (la prueba con la BD
        // real, TareaSchedulerTransaccionTest, lo comprueba); aquí basta con que el
        // fallo no impida avisar y marcar la siguiente.
        assertThat(buena.getNotificadoProximoVencimiento()).isTrue();
        verify(tareas).saveAndFlush(buena);
        verify(notificaciones).save(org.mockito.ArgumentMatchers.argThat(n -> n != null && n.getTarea() == buena));
    }

    @Test
    void una_tarea_ya_avisada_entre_la_consulta_y_su_transaccion_no_se_avisa_otra_vez() {
        mt_NotificacionRepository notificaciones = mock(mt_NotificacionRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        mt_Tarea avisada = tarea(1, Usuario.builder().id(5).nombre("Comercial").build());
        avisada.setNotificadoProximoVencimiento(true);
        when(tareas.findTareasParaNotificarVencimiento(any(), any())).thenReturn(List.of(avisada));

        servicio(notificaciones, usuarios).verificarTareasProximasAVencer();

        verify(notificaciones, never()).save(any(mt_Notificacion.class));
    }

    @Test
    void si_mientras_tanto_se_movio_el_vencimiento_o_se_quito_el_aviso_no_se_avisa() {
        mt_NotificacionRepository notificaciones = mock(mt_NotificacionRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario comercial = Usuario.builder().id(5).nombre("Comercial").build();
        mt_Tarea movida = tarea(1, comercial);
        mt_Tarea sinAviso = tarea(2, comercial);
        // Lo que se relee tras el bloqueo: la una vence ya a las tantas, la otra no avisa
        movida.setFechaVencimiento(LocalDateTime.now().plusHours(5));
        sinAviso.setNotificar(false);
        when(tareas.findTareasParaNotificarVencimiento(any(), any())).thenReturn(List.of(movida, sinAviso));

        servicio(notificaciones, usuarios).verificarTareasProximasAVencer();

        verify(notificaciones, never()).save(any(mt_Notificacion.class));
        assertThat(movida.getNotificadoProximoVencimiento()).as("su aviso de 1 hora antes sigue pendiente").isNotEqualTo(Boolean.TRUE);
    }
}
