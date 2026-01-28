package api.aura.controller.modulo_tareas;

import api.aura.dto.modulo_tareas.NotificacionDTO;
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

    @GetMapping("/usuario/{usuarioId}")
    public List<NotificacionDTO> porUsuario(@PathVariable Integer usuarioId) {
        return notificacionService.obtenerPorUsuario(usuarioId);
    }

    @GetMapping("/usuario/{usuarioId}/no-leidas")
    public List<NotificacionDTO> noLeidas(@PathVariable Integer usuarioId) {
        return notificacionService.obtenerNoLeidas(usuarioId);
    }

    @GetMapping("/usuario/{usuarioId}/count-no-leidas")
    public ResponseEntity<?> countNoLeidas(@PathVariable Integer usuarioId) {
        long count = notificacionService.contarNoLeidas(usuarioId);
        return ResponseEntity.ok(new CountResponse(count));
    }

    @PatchMapping("/{id}/marcar-leida")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id) {
        notificacionService.marcarComoLeida(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/usuario/{usuarioId}/marcar-todas-leidas")
    public ResponseEntity<Void> marcarTodas(@PathVariable Integer usuarioId) {
        notificacionService.marcarTodasComoLeidas(usuarioId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        notificacionService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private record CountResponse(long count) {
    }
}
