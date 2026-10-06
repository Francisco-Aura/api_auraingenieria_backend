package api.aura.service.modulo_tareas;

import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Notificacion;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.mt_NotificacionRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TareaSchedulerServiceTest {

    private static mt_Tarea tarea(long id, Usuario asignado) {
        return mt_Tarea.builder().id(id).titulo("Tarea " + id).usuarioAsignado(asignado)
                .fechaVencimiento(LocalDateTime.now().plusMinutes(30)).build();
    }

    @Test
    void los_administradores_se_consultan_una_vez_por_ejecucion_y_se_notifica_a_todos() {
        mt_TareaRepository tareas = mock(mt_TareaRepository.class);
        mt_NotificacionRepository notificaciones = mock(mt_NotificacionRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario comercial = Usuario.builder().id(5).nombre("Comercial").build();
        Usuario admin = Usuario.builder().id(1).nombre("Admin").nivelAcceso(1).build();
        when(tareas.findTareasParaNotificarVencimiento(any(), any()))
                .thenReturn(List.of(tarea(1, comercial), tarea(2, comercial), tarea(3, admin)));
        when(usuarios.findByNivelAcceso(1)).thenReturn(List.of(admin));

        new TareaSchedulerService(tareas, notificaciones, usuarios).verificarTareasProximasAVencer();

        verify(usuarios, times(1)).findByNivelAcceso(1);
        // Tareas 1 y 2: asignado + administrador; tarea 3: el administrador es el asignado.
        verify(notificaciones, times(5)).save(any(mt_Notificacion.class));
    }

    @Test
    void notificar_1h_antes_solo_busca_las_tareas_que_vencen_en_la_proxima_hora() {
        mt_TareaRepository tareas = mock(mt_TareaRepository.class);
        mt_NotificacionRepository notificaciones = mock(mt_NotificacionRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario comercial = Usuario.builder().id(5).nombre("Comercial").build();
        when(tareas.findTareasParaNotificarVencimiento(any(), any())).thenReturn(List.of(tarea(1, comercial)));

        new TareaSchedulerService(tareas, notificaciones, usuarios).verificarTareasProximasAVencer();

        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> hasta = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tareas).findTareasParaNotificarVencimiento(desde.capture(), hasta.capture());
        assertThat(Duration.between(desde.getValue(), hasta.getValue())).isEqualTo(Duration.ofHours(1));
        ArgumentCaptor<mt_Notificacion> aviso = ArgumentCaptor.forClass(mt_Notificacion.class);
        verify(notificaciones).save(aviso.capture());
        assertThat(aviso.getValue().getMensaje()).contains("vence a las").contains("en menos de 1 hora");
    }
}
