package api.aura.controller.modulo_tareas;

import api.aura.controller.SesionActual;
import api.aura.dto.modulo_tareas.NotificacionDTO;
import api.aura.repository.modulo_tareas.mt_NotificacionRepository;
import api.aura.dto.modulo_tareas.PlanificadorAvisoRequest;
import api.aura.service.modulo_tareas.TareaNotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notificaciones-tareas")
@RequiredArgsConstructor
public class TareaNotificacionController {

    private final TareaNotificacionService notificacionService;
    private final mt_NotificacionRepository notificacionRepository;

    // Cada usuario solo ve y gestiona sus propias notificaciones (los
    // administradores, cualquiera). El usuarioId de la URL no basta.

    /**
     * Clave compartida con el planificador (variable de entorno PLANIFICADOR_TOKEN). Si no está definida, el
     * endpoint del planificador queda desactivado.
     */
    @Value("${planificador.token:}")
    private String planificadorToken;

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

    /**
     * Aviso del planificador (una tarea ha terminado y otras pueden empezar). Lo llama el servidor del
     * planificador, no el navegador: exige la clave compartida en la cabecera X-Planificador-Token.
     */
    @PostMapping("/planificador")
    public ResponseEntity<?> desdePlanificador(
            @RequestHeader(value = "X-Planificador-Token", required = false) String token,
            @RequestBody PlanificadorAvisoRequest aviso) {
        if (!tokenValido(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (aviso.getGroup() == null || aviso.getGroup().isBlank()
                || aviso.getMessage() == null || aviso.getMessage().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Faltan el grupo o el mensaje"));
        }
        int creadas = notificacionService.notificarDesdePlanificador(aviso);
        return ResponseEntity.ok(new CreadasResponse(creadas));
    }

    /** Comparación en tiempo constante; sin clave configurada nunca es válida. */
    private boolean tokenValido(String recibido) {
        if (planificadorToken == null || planificadorToken.isBlank() || recibido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                planificadorToken.getBytes(StandardCharsets.UTF_8), recibido.getBytes(StandardCharsets.UTF_8));
    }

    private record CountResponse(long count) {
    }

    private record CreadasResponse(int creadas) {
    }
}
