package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.NotificacionDTO;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Notificacion;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.repository.modulo_tareas.mt_NotificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TareaNotificacionService {

    private final mt_NotificacionRepository notificacionRepository;

    public void notificarAsignacion(mt_Tarea tarea) {
        if (tarea.getUsuarioAsignado() == null) {
            return;
        }
        crearNotificacion(tarea, tarea.getUsuarioAsignado(), mt_Notificacion.TipoNotificacion.ASIGNADA,
                String.format("Se te ha asignado la tarea '%s'", tarea.getTitulo()));
    }

    public void notificarCambioEstado(mt_Tarea tarea, String estadoDestino) {
        if (tarea.getUsuarioAsignado() == null) {
            return;
        }
        crearNotificacion(tarea, tarea.getUsuarioAsignado(), mt_Notificacion.TipoNotificacion.CAMBIO_ESTADO,
                String.format("La tarea '%s' cambió a %s", tarea.getTitulo(), estadoDestino));
    }

    public void notificarVencimiento(mt_Tarea tarea) {
        if (tarea.getUsuarioAsignado() == null) {
            return;
        }
        crearNotificacion(tarea, tarea.getUsuarioAsignado(), mt_Notificacion.TipoNotificacion.VENCIMIENTO,
                String.format("La tarea '%s' está vencida", tarea.getTitulo()));
    }

    public void notificarComentario(mt_Tarea tarea, Usuario autor, String comentario) {
        if (tarea.getUsuarioAsignado() == null || autor == null
                || tarea.getUsuarioAsignado().getId().equals(autor.getId())) {
            return;
        }
        crearNotificacion(tarea, tarea.getUsuarioAsignado(), mt_Notificacion.TipoNotificacion.COMENTARIO,
                String.format("%s comentó en '%s': %s", autor.getNombre(), tarea.getTitulo(), comentario));
    }

    public void notificarCambioTarea(mt_Tarea tarea, String detallesCambio) {
        if (tarea.getUsuarioAsignado() == null) {
            return;
        }
        crearNotificacion(tarea, tarea.getUsuarioAsignado(), mt_Notificacion.TipoNotificacion.CAMBIO_ESTADO,
                String.format("La tarea '%s' fue modificada: %s", tarea.getTitulo(), detallesCambio));
    }

    public void notificarCampoEspecifico(mt_Tarea tarea, String tipoCampo, String mensaje) {
        if (tarea.getUsuarioAsignado() == null) {
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
        notificacionRepository.findByUsuarioDestino_IdAndLeidaFalseOrderByFechaCreacionDesc(usuarioId)
                .forEach(notificacion -> {
                    notificacion.setLeida(Boolean.TRUE);
                    notificacionRepository.save(notificacion);
                });
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
