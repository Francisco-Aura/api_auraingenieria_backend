package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.NotificacionDTO;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Notificacion;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.mt_NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TareaNotificacionService {

    private final mt_NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public void notificarAsignacion(mt_Tarea tarea) {
        notificarAsignacion(tarea, null);
    }

    public void notificarAsignacion(mt_Tarea tarea, Integer usuarioAccionId) {
        if (tarea.getUsuarioAsignado() == null) {
            return;
        }
        // No notificar si el usuario que asigna es el mismo que el asignado
        if (usuarioAccionId != null && usuarioAccionId.equals(tarea.getUsuarioAsignado().getId())) {
            return;
        }
        crearNotificacion(tarea, tarea.getUsuarioAsignado(), mt_Notificacion.TipoNotificacion.ASIGNADA,
                String.format("Se te ha asignado la tarea '%s'", tarea.getTitulo()));
    }

    public void notificarCambioEstado(mt_Tarea tarea, String estadoDestino) {
        notificarCambioEstado(tarea, estadoDestino, null);
    }

    public void notificarCambioEstado(mt_Tarea tarea, String estadoDestino, Integer usuarioAccionId) {
        if (tarea.getUsuarioAsignado() == null) {
            return;
        }
        // No notificar si el usuario que cambia el estado es el mismo que el asignado
        if (usuarioAccionId != null && usuarioAccionId.equals(tarea.getUsuarioAsignado().getId())) {
            return;
        }
        crearNotificacion(tarea, tarea.getUsuarioAsignado(), mt_Notificacion.TipoNotificacion.CAMBIO_ESTADO,
                String.format("La tarea '%s' cambió a %s", tarea.getTitulo(), estadoDestino));
    }

    public void notificarVencimiento(mt_Tarea tarea) {
        String mensaje = String.format("La tarea '%s' está vencida", tarea.getTitulo());

        if (tarea.getUsuarioAsignado() != null) {
            crearNotificacion(tarea, tarea.getUsuarioAsignado(), mt_Notificacion.TipoNotificacion.VENCIMIENTO, mensaje);
        }

        usuarioRepository.findByNivelAcceso(1).forEach(admin -> {
            if (tarea.getUsuarioAsignado() != null && tarea.getUsuarioAsignado().getId().equals(admin.getId())) {
                return;
            }
            crearNotificacion(tarea, admin, mt_Notificacion.TipoNotificacion.VENCIMIENTO, mensaje);
        });
    }

    public void notificarComentario(mt_Tarea tarea, Usuario autor, String comentario) {
        if (tarea.getUsuarioAsignado() == null || autor == null
                || tarea.getUsuarioAsignado().getId().equals(autor.getId())) {
            return;
        }
        // Truncar comentario a 180 caracteres para caber en VARCHAR(255) con el prefijo
        String comentarioTruncado = comentario;
        if (comentario != null && comentario.length() > 180) {
            comentarioTruncado = comentario.substring(0, 180) + "...";
        }
        crearNotificacion(tarea, tarea.getUsuarioAsignado(), mt_Notificacion.TipoNotificacion.COMENTARIO,
                String.format("%s comentó: %s", autor.getNombre(), comentarioTruncado));
    }

    /**
     * Notifica a todos los administradores (nivelAcceso = 1) que una tarea fue completada.
     * No notifica al propio responsable de la acción si también es admin.
     */
    public void notificarCompletada(mt_Tarea tarea, Usuario autor) {
        String mensaje = autor != null
                ? String.format("%s completó la tarea '%s'", autor.getNombre(), tarea.getTitulo())
                : String.format("La tarea '%s' fue completada", tarea.getTitulo());

        usuarioRepository.findByNivelAcceso(1).forEach(admin -> {
            // No auto-notificar al admin que realizó la acción
            if (autor != null && autor.getId().equals(admin.getId())) {
                return;
            }
            crearNotificacion(tarea, admin, mt_Notificacion.TipoNotificacion.COMPLETADA, mensaje);
        });
    }

    /**
     * Notifica a un usuario mencionado con @ en un comentario.
     */
    public void notificarMencion(mt_Tarea tarea, Usuario mencionado, Usuario autor) {
        if (mencionado == null || autor == null) {
            return;
        }
        if (mencionado.getId().equals(autor.getId())) {
            return;
        }
        String mensaje = String.format("%s te mencionó en la tarea '%s'", autor.getNombre(), tarea.getTitulo());
        crearNotificacion(tarea, mencionado, mt_Notificacion.TipoNotificacion.MENCION, mensaje);
    }

    public void notificarCambioTarea(mt_Tarea tarea, String detallesCambio) {
        notificarCambioTarea(tarea, detallesCambio, null);
    }

    public void notificarCambioTarea(mt_Tarea tarea, String detallesCambio, Integer usuarioAccionId) {
        if (tarea.getUsuarioAsignado() == null) {
            return;
        }
        // No notificar si el usuario que modifica es el mismo que el asignado
        if (usuarioAccionId != null && usuarioAccionId.equals(tarea.getUsuarioAsignado().getId())) {
            return;
        }
        crearNotificacion(tarea, tarea.getUsuarioAsignado(), mt_Notificacion.TipoNotificacion.CAMBIO_ESTADO,
                String.format("La tarea '%s' fue modificada: %s", tarea.getTitulo(), detallesCambio));
    }

    public void notificarCampoEspecifico(mt_Tarea tarea, String tipoCampo, String mensaje) {
        notificarCampoEspecifico(tarea, tipoCampo, mensaje, null);
    }

    public void notificarCampoEspecifico(mt_Tarea tarea, String tipoCampo, String mensaje, Integer usuarioAccionId) {
        if (tarea.getUsuarioAsignado() == null) {
            return;
        }
        // No notificar si el usuario que modifica es el mismo que el asignado
        if (usuarioAccionId != null && usuarioAccionId.equals(tarea.getUsuarioAsignado().getId())) {
            return;
        }
        // Usar el tipo CAMBIO_ESTADO para campos modificados, será diferenciado por el
        // mensaje
        crearNotificacion(tarea, tarea.getUsuarioAsignado(), mt_Notificacion.TipoNotificacion.CAMBIO_ESTADO, mensaje);
    }

    public List<NotificacionDTO> obtenerPorUsuario(Integer usuarioId) {
        return notificacionRepository.findByUsuarioDestino_IdOrderByFechaCreacionDesc(usuarioId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<NotificacionDTO> obtenerNoLeidas(Integer usuarioId) {
        return notificacionRepository.findByUsuarioDestino_IdAndLeidaFalseOrderByFechaCreacionDesc(usuarioId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public long contarNoLeidas(Integer usuarioId) {
        return notificacionRepository.countByUsuarioDestino_IdAndLeidaFalse(usuarioId);
    }

    public void marcarComoLeida(Long notificacionId) {
        notificacionRepository.findById(notificacionId).ifPresent(notificacion -> {
            notificacion.setLeida(Boolean.TRUE);
            notificacionRepository.save(notificacion);
        });
    }

    public void marcarTodasComoLeidas(Integer usuarioId) {
        notificacionRepository.marcarTodasComoLeidasByUsuarioId(usuarioId);
    }

    public void eliminar(Long id) {
        notificacionRepository.deleteById(id);
    }

    private void crearNotificacion(mt_Tarea tarea, Usuario destinatario, mt_Notificacion.TipoNotificacion tipo,
            String mensaje) {
        if (destinatario == null || destinatario.getId() == null) {
            return;
        }
        mt_Notificacion notificacion = mt_Notificacion.builder()
                .tarea(tarea)
                .usuarioDestino(destinatario)
                .tipo(tipo)
                .mensaje(mensaje)
                .build();
        notificacionRepository.save(notificacion);
        // Enviar push WebSocket DESPUÉS del commit para garantizar que la BD ya tiene el registro
        final NotificacionDTO dto = toDto(notificacion);
        final Integer destinatarioId = destinatario.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    messagingTemplate.convertAndSend("/topic/notificaciones/" + destinatarioId, dto);
                } catch (Exception e) {
                    log.warn("No se pudo enviar notificación WebSocket al usuario {}: {}", destinatarioId, e.getMessage());
                }
            }
        });
    }

    private NotificacionDTO toDto(mt_Notificacion notificacion) {
        return NotificacionDTO.builder()
                .id(notificacion.getId())
                .tareaId(notificacion.getTarea().getId())
                .tareaTitulo(notificacion.getTarea().getTitulo())
                .tipo(notificacion.getTipo())
                .mensaje(notificacion.getMensaje())
                .leida(notificacion.getLeida())
                .fechaCreacion(notificacion.getFechaCreacion())
                .build();
    }
}
