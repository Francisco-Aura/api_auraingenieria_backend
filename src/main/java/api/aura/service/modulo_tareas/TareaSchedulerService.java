package api.aura.service.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.entity.modulo_tareas.mt_Notificacion;
import api.aura.entity.Usuario;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import api.aura.repository.modulo_tareas.mt_NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TareaSchedulerService {

    private final mt_TareaRepository tareaRepository;
    private final mt_NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Ejecuta cada 5 minutos para verificar tareas que vencen HOY.
     * Notifica en cuanto la tarea entra en el día actual (ventana: ahora → fin de hoy).
     * El flag notificadoProximoVencimiento evita notificaciones duplicadas.
     */
    @Scheduled(fixedRate = 300000) // Cada 5 minutos (300000 ms)
    @Transactional
    public void verificarTareasProximasAVencer() {
        log.debug("Ejecutando verificación de tareas que vencen hoy...");

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime finDeHoy = LocalDate.now().atTime(23, 59, 59);

        List<mt_Tarea> tareasProximasVencer = tareaRepository
                .findTareasParaNotificarVencimiento(ahora, finDeHoy);

        if (tareasProximasVencer.isEmpty()) {
            log.debug("No hay tareas que venzan hoy pendientes de notificación");
            return;
        }

        log.info("Encontradas {} tareas que vencen hoy", tareasProximasVencer.size());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for (mt_Tarea tarea : tareasProximasVencer) {
            try {
                enviarNotificacionVencimiento(tarea, formatter, ahora);

                // Marcar que ya se notificó para evitar duplicados
                tarea.setNotificadoProximoVencimiento(true);
                tareaRepository.save(tarea);

                log.info("Notificación enviada para tarea: {} (ID: {})", tarea.getTitulo(), tarea.getId());
            } catch (Exception e) {
                log.error("Error al enviar notificación para tarea {}: {}", tarea.getId(), e.getMessage());
            }
        }
    }

    private void enviarNotificacionVencimiento(mt_Tarea tarea, DateTimeFormatter formatter, LocalDateTime ahora) {
        String horaVencimiento = tarea.getFechaVencimiento().format(formatter);
        long minutosRestantes = java.time.Duration.between(ahora, tarea.getFechaVencimiento()).toMinutes();

        String mensaje;
        if (minutosRestantes <= 60) {
            mensaje = String.format("⏰ La tarea '%s' vence a las %s (en menos de 1 hora)", tarea.getTitulo(), horaVencimiento);
        } else {
            mensaje = String.format("📅 La tarea '%s' vence hoy a las %s", tarea.getTitulo(), horaVencimiento);
        }

        Usuario asignado = tarea.getUsuarioAsignado();

        if (asignado != null && asignado.getId() != null) {
            guardarNotificacion(tarea, asignado, mensaje);
        }

        usuarioRepository.findByNivelAcceso(1).forEach(admin -> {
            if (asignado != null && asignado.getId().equals(admin.getId())) {
                return;
            }
            guardarNotificacion(tarea, admin, mensaje);
        });
    }

    private void guardarNotificacion(mt_Tarea tarea, Usuario destinatario, String mensaje) {
        mt_Notificacion notificacion = mt_Notificacion.builder()
                .tarea(tarea)
                .usuarioDestino(destinatario)
                .tipo(mt_Notificacion.TipoNotificacion.VENCIMIENTO)
                .mensaje(mensaje)
                .build();
        notificacionRepository.save(notificacion);
    }

    /**
     * Reinicia el flag de notificación cada día a medianoche para todas las tareas
     * pendientes, de modo que vuelvan a notificarse cuando llegue su día de vencimiento.
     */
    @Scheduled(cron = "0 1 0 * * *") // Cada día a las 00:01
    @Transactional
    public void reiniciarFlagsNotificacionDiarios() {
        log.info("Reiniciando flags de notificación de vencimiento para el nuevo día...");
        int actualizadas = tareaRepository.reiniciarFlagsNotificacionVencimiento();
        log.info("Reiniciados {} flags de notificación", actualizadas);
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
