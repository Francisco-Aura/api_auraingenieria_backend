package api.aura.service.modulo_tareas;

import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Notificacion;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.entity.modulo_tareas.mt_TareaEstado;
import api.aura.entity.modulo_tareas.mt_TareaHistorial;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.mt_NotificacionRepository;
import api.aura.repository.modulo_tareas.mt_TareaEstadoRepository;
import api.aura.repository.modulo_tareas.mt_TareaHistorialRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Con la BD de verdad: una tarea con historial y avisos se puede eliminar. Antes el
 * borrado chocaba con las claves foráneas del historial y de las notificaciones
 * (sin ON DELETE CASCADE) y el usuario recibía un error de conflicto de datos.
 */
@SpringBootTest
class TareaEliminarConHistorialTest {

    @Autowired private TareaService servicio;
    @Autowired private mt_TareaRepository tareas;
    @Autowired private mt_TareaEstadoRepository estados;
    @Autowired private mt_TareaHistorialRepository historial;
    @Autowired private mt_NotificacionRepository notificaciones;
    @Autowired private UsuarioRepository usuarios;

    @Test
    void se_elimina_con_su_historial_y_sus_avisos() {
        mt_TareaEstado estado = estados.save(mt_TareaEstado.builder().nombre("ELIMINAR-" + System.nanoTime())
                .estadoFinal(false).orden(60).cuentaTiempo(false).esBloqueo(false).build());
        Usuario usuario = usuarios.save(Usuario.builder().nombre("Usuario borrado tarea").build());
        mt_Tarea tarea = tareas.save(mt_Tarea.builder().titulo("Tarea a eliminar").prioridad(2)
                .notificar(false).enviarCorreo(false).estado(estado)
                .usuarioAsignado(usuario).usuarioCreador(usuario).build());
        historial.save(mt_TareaHistorial.builder().tarea(tarea).estado(estado).usuarioAccion(usuario)
                .fechaInicio(LocalDateTime.now()).build());
        notificaciones.save(mt_Notificacion.builder().tarea(tarea).usuarioDestino(usuario)
                .tipo(mt_Notificacion.TipoNotificacion.ASIGNADA).mensaje("Te han asignado una tarea")
                .leida(false).fechaCreacion(LocalDateTime.now()).build());

        servicio.eliminar(tarea.getId());

        assertThat(tareas.findById(tarea.getId())).isEmpty();
        assertThat(historial.findByTarea_IdOrderByFechaInicioDescIdDesc(tarea.getId())).isEmpty();
        assertThat(notificaciones.findAll()).noneMatch(n -> n.getTarea().getId().equals(tarea.getId()));
    }
}
