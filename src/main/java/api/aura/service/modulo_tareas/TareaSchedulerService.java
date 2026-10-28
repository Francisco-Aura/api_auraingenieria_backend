package api.aura.service.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.entity.modulo_tareas.mt_Notificacion;
import api.aura.entity.Usuario;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import api.aura.repository.modulo_tareas.mt_NotificacionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class TareaSchedulerService {

    private final mt_TareaRepository tareaRepository;
    private final mt_NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    /** Envía los avisos al navegador al instante, como el resto de notificaciones. */
    private final TareaNotificacionService avisos;
    /** Una transacción nueva por tarea avisada (ver verificarTareasProximasAVencer). */
    private final TransactionTemplate transaccionPorTarea;
    /** Evita dos verificaciones a la vez si el planificador llegara a solaparlas. */
    private final AtomicBoolean verificando = new AtomicBoolean(false);

    public TareaSchedulerService(mt_TareaRepository tareaRepository,
            mt_NotificacionRepository notificacionRepository,
            UsuarioRepository usuarioRepository,
            TareaNotificacionService avisos,
            PlatformTransactionManager transactionManager) {
        this.tareaRepository = tareaRepository;
        this.notificacionRepository = notificacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.avisos = avisos;
        this.transaccionPorTarea = new TransactionTemplate(transactionManager);
        this.transaccionPorTarea.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * «Notificar 1h antes»: cada 5 minutos busca las tareas con aviso activado que
     * vencen en la próxima hora (ventana: ahora → ahora + 1 h) y avisa una sola vez.
     * El flag notificadoProximoVencimiento evita notificaciones duplicadas.
     *
     * <p>Cada tarea se avisa y se marca en su propia transacción. Antes todo iba en
     * una sola: un error de BD con una tarea (atrapado en el bucle) dejaba la
     * transacción marcada para deshacer, el commit final lanzaba
     * UnexpectedRollbackException y se perdían los avisos de todas las tareas de la
     * pasada (y en la siguiente volvía a pasar lo mismo).</p>
     *
     * <p>Con el planificador por defecto (un hilo, {@code fixedRate}) dos pasadas
     * nunca se solapan; la guarda lo mantiene así aunque se cambie el planificador
     * (p. ej. hilos virtuales, donde {@code fixedRate} sí puede solaparse).</p>
     */
    @Scheduled(fixedRate = 300000) // Cada 5 minutos (300000 ms)
    public void verificarTareasProximasAVencer() {
        if (!verificando.compareAndSet(false, true)) {
            log.warn("La verificación anterior de vencimientos sigue en curso: se omite esta pasada");
            return;
        }
        try {
            verificarVencimientos();
        } finally {
            verificando.set(false);
        }
    }

    private void verificarVencimientos() {
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

        for (mt_Tarea encontrada : tareasProximasVencer) {
            Long tareaId = encontrada.getId();
            try {
                Boolean avisada = transaccionPorTarea.execute(
                        estado -> notificarVencimiento(tareaId, ahora, dentroDeUnaHora, formatter, administradores));
                if (Boolean.TRUE.equals(avisada)) {
                    log.info("Notificación enviada para tarea: {} (ID: {})", encontrada.getTitulo(), tareaId);
                }
            } catch (Exception e) {
                log.error("Error al enviar notificación para tarea {}", tareaId, e);
            }
        }
    }

    /**
     * Avisa de una tarea y la marca como avisada, dentro de la transacción de esa
     * tarea. Se relee la tarea para marcar la versión actual (y no pisar con la
     * copia de la consulta un cambio hecho mientras tanto); si ya no existe o ya
     * se avisó, no hace nada.
     */
    private boolean notificarVencimiento(Long tareaId, LocalDateTime desde, LocalDateTime hasta,
                                         DateTimeFormatter formatter, List<Usuario> administradores) {
        // La fila se bloquea antes de leerla: una edición a la vez esperaba a que esta
        // terminase y después volvía a dejar la tarea como no avisada (aviso doble)
        tareaRepository.bloquear(tareaId);
        mt_Tarea tarea = tareaRepository.findById(tareaId).orElse(null);
        // Releída tras el bloqueo, tiene que seguir cumpliendo lo mismo que la consulta: si
        // mientras tanto se cambió el vencimiento, se completó o se quitó el aviso, no se
        // avisa (antes avisaba de la hora nueva y el aviso bueno ya no llegaba)
        if (tarea == null || Boolean.TRUE.equals(tarea.getNotificadoProximoVencimiento())
                || !Boolean.TRUE.equals(tarea.getNotificar())
                || (tarea.getEstado() != null && Boolean.TRUE.equals(tarea.getEstado().getEstadoFinal()))
                || tarea.getFechaVencimiento() == null
                || tarea.getFechaVencimiento().isBefore(desde) || tarea.getFechaVencimiento().isAfter(hasta)) {
            return false;
        }
        // Primero se marca como avisada (bloquea la fila de la tarea) y después se
        // insertan los avisos: en el orden inverso, una edición o un borrado de la
        // misma tarea a la vez podía acabar en un interbloqueo en la BD. Si algo
        // falla, la transacción de la tarea deshace las dos cosas.
        tarea.setNotificadoProximoVencimiento(true);
        tareaRepository.saveAndFlush(tarea);
        enviarNotificacionVencimiento(tarea, formatter, administradores);
        return true;
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
        // Antes solo se guardaba: el aviso «vence en menos de 1 hora» no llegaba a la
        // campana hasta recargar la página o abrirla
        avisos.enviarTrasConfirmar(notificacion);
    }

    /**
     * Reinicia el flag de notificación cada día a medianoche para todas las tareas
     * pendientes, de modo que vuelvan a notificarse cuando llegue su día de vencimiento.
     */
    @Scheduled(cron = "0 1 0 * * *") // Cada día a las 00:01
    @Transactional
    public void reiniciarFlagsNotificacionDiarios() {
        log.info("Reiniciando flags de notificación de vencimiento para el nuevo día...");
        int actualizadas = tareaRepository.reiniciarFlagsNotificacionVencimiento(LocalDateTime.now().plusHours(1));
        log.info("Reiniciados {} flags de notificación", actualizadas);
    }
}
