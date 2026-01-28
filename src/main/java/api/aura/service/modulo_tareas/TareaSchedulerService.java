package api.aura.service.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.entity.modulo_tareas.mt_Notificacion;
import api.aura.entity.Usuario;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import api.aura.repository.modulo_tareas.mt_NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TareaSchedulerService {

    private final mt_TareaRepository tareaRepository;
    private final mt_NotificacionRepository notificacionRepository;

    /**
     * Ejecuta cada 5 minutos para verificar tareas que vencen en la próxima hora.
     * Envía notificaciones a los usuarios asignados si tienen activada la opción
     * "notificar".
     */
    @Scheduled(fixedRate = 300000) // Cada 5 minutos (300000 ms)
    @Transactional
    public void verificarTareasProximasAVencer() {
        log.debug("Ejecutando verificación de tareas próximas a vencer...");

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime unaHoraDespues = ahora.plusHours(1);

        List<mt_Tarea> tareasProximasVencer = tareaRepository
                .findTareasParaNotificarVencimiento(ahora, unaHoraDespues);

        if (tareasProximasVencer.isEmpty()) {
            log.debug("No hay tareas próximas a vencer que requieran notificación");
            return;
        }

        log.info("Encontradas {} tareas próximas a vencer", tareasProximasVencer.size());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for (mt_Tarea tarea : tareasProximasVencer) {
            try {
                enviarNotificacionVencimiento(tarea, formatter);

                // Marcar que ya se notificó para evitar duplicados
                tarea.setNotificadoProximoVencimiento(true);
                tareaRepository.save(tarea);

                log.info("Notificación enviada para tarea: {} (ID: {})", tarea.getTitulo(), tarea.getId());
            } catch (Exception e) {
                log.error("Error al enviar notificación para tarea {}: {}", tarea.getId(), e.getMessage());
            }
        }
    }

    private void enviarNotificacionVencimiento(mt_Tarea tarea, DateTimeFormatter formatter) {
        Usuario destinatario = tarea.getUsuarioAsignado();

        if (destinatario == null || destinatario.getId() == null) {
            log.warn("Tarea {} no tiene usuario asignado, omitiendo notificación", tarea.getId());
            return;
        }

        String horaVencimiento = tarea.getFechaVencimiento().format(formatter);
        String mensaje = String.format("⏰ La tarea '%s' vence a las %s (en menos de 1 hora)",
                tarea.getTitulo(), horaVencimiento);

        mt_Notificacion notificacion = mt_Notificacion.builder()
                .tarea(tarea)
                .usuarioDestino(destinatario)
                .tipo(mt_Notificacion.TipoNotificacion.VENCIMIENTO)
                .mensaje(mensaje)
                .build();

        notificacionRepository.save(notificacion);
    }

    /**
     * Reinicia el flag de notificación cuando se actualiza la fecha de vencimiento.
     * Llamar este método desde TareaService cuando se modifica fechaVencimiento.
     */
    public void reiniciarNotificacionVencimiento(mt_Tarea tarea) {
        if (tarea.getNotificadoProximoVencimiento() != null && tarea.getNotificadoProximoVencimiento()) {
            tarea.setNotificadoProximoVencimiento(false);
            log.debug("Reiniciado flag de notificación para tarea {} por cambio de fecha", tarea.getId());
        }
    }
}
