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
     * «Notificar 1h antes»: cada 5 minutos busca las tareas con aviso activado que
     * vencen en la próxima hora (ventana: ahora → ahora + 1 h) y avisa una sola vez.
     * El flag notificadoProximoVencimiento evita notificaciones duplicadas.
     */
    @Scheduled(fixedRate = 300000) // Cada 5 minutos (300000 ms)
    @Transactional
    public void verificarTareasProximasAVencer() {
        log.debug("Ejecutando verificación de tareas que vencen en la próxima hora...");

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime dentroDeUnaHora = ahora.plusHours(1);

        List<mt_Tarea> tareasProximasVencer = tareaRepository
                .findTareasParaNotificarVencimiento(ahora, dentroDeUnaHora);

        if (tareasProximasVencer.isEmpty()) {
            log.debug("No hay tareas que venzan en la próxima hora pendientes de notificación");
            return;
        }

        log.info("Encontradas {} tareas que vencen en la próxima hora", tareasProximasVencer.size());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        // Los administradores no cambian durante la ejecución: una sola consulta
        List<Usuario> administradores = usuarioRepository.findByNivelAcceso(1);

        for (mt_Tarea tarea : tareasProximasVencer) {
            try {
                enviarNotificacionVencimiento(tarea, formatter, administradores);

                // Marcar que ya se notificó para evitar duplicados
                tarea.setNotificadoProximoVencimiento(true);
                tareaRepository.save(tarea);

                log.info("Notificación enviada para tarea: {} (ID: {})", tarea.getTitulo(), tarea.getId());
            } catch (Exception e) {
                log.error("Error al enviar notificación para tarea {}: {}", tarea.getId(), e.getMessage());
            }
        }
    }

    private void enviarNotificacionVencimiento(mt_Tarea tarea, DateTimeFormatter formatter,
            List<Usuario> administradores) {
        String horaVencimiento = tarea.getFechaVencimiento().format(formatter);
        String mensaje = String.format("⏰ La tarea '%s' vence a las %s (en menos de 1 hora)", tarea.getTitulo(), horaVencimiento);

        Usuario asignado = tarea.getUsuarioAsignado();

        if (asignado != null && asignado.getId() != null) {
            guardarNotificacion(tarea, asignado, mensaje);
        }

        administradores.forEach(admin -> {
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
}
