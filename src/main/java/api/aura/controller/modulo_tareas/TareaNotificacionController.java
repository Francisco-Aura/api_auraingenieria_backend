package api.aura.controller.modulo_tareas;

import api.aura.controller.SesionActual;
import api.aura.dto.modulo_tareas.NotificacionDTO;
import api.aura.repository.modulo_tareas.mt_NotificacionRepository;
import api.aura.service.modulo_tareas.TareaNotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones-tareas")
@RequiredArgsConstructor
public class TareaNotificacionController {

    private final TareaNotificacionService notificacionService;
    private final mt_NotificacionRepository notificacionRepository;

    // Cada usuario solo ve y gestiona sus propias notificaciones (los
    // administradores, cualquiera). El usuarioId de la URL no basta.

    @GetMapping("/usuario/{usuarioId}")
    public List<NotificacionDTO> porUsuario(@PathVariable Integer usuarioId) {
        SesionActual.exigirPropioOAdmin(usuarioId);
        return notificacionService.obtenerPorUsuario(usuarioId);
    }

    @GetMapping("/usuario/{usuarioId}/no-leidas")
    public List<NotificacionDTO> noLeidas(@PathVariable Integer usuarioId) {
        SesionActual.exigirPropioOAdmin(usuarioId);
        return notificacionService.obtenerNoLeidas(usuarioId);
    }

    @GetMapping("/usuario/{usuarioId}/count-no-leidas")
    public ResponseEntity<?> countNoLeidas(@PathVariable Integer usuarioId) {
        SesionActual.exigirPropioOAdmin(usuarioId);
        long count = notificacionService.contarNoLeidas(usuarioId);
        return ResponseEntity.ok(new CountResponse(count));
    }

    @PatchMapping("/{id}/marcar-leida")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id) {
        exigirPropietario(id);
        notificacionService.marcarComoLeida(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/usuario/{usuarioId}/marcar-todas-leidas")
    public ResponseEntity<Void> marcarTodas(@PathVariable Integer usuarioId) {
        SesionActual.exigirPropioOAdmin(usuarioId);
        notificacionService.marcarTodasComoLeidas(usuarioId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        exigirPropietario(id);
        notificacionService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** Si la notificación existe, debe ser del usuario de la sesión (o ser admin). */
    private void exigirPropietario(Long notificacionId) {
        notificacionRepository.findUsuarioDestinoIdById(notificacionId)
                .ifPresent(SesionActual::exigirPropioOAdmin);
    }

    private record CountResponse(long count) {
    }
}
