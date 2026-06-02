package api.aura.controller.modulo_tareas;

import api.aura.dto.modulo_tareas.ClienteActividadDTO;
import api.aura.dto.modulo_tareas.ClienteNotaDTO;
import api.aura.dto.modulo_tareas.ActualizarNotaClienteRequest;
import api.aura.dto.modulo_tareas.CrearNotaClienteRequest;
import api.aura.dto.modulo_tareas.TareaDTO;
import api.aura.service.modulo_tareas.ClienteTimelineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes/{clienteId}/timeline")
@RequiredArgsConstructor
public class ClienteTimelineController {

    private final ClienteTimelineService clienteTimelineService;

    @GetMapping("/actividad")
    public ResponseEntity<?> obtenerActividad(@PathVariable Integer clienteId,
            @RequestParam(required = false) Integer nivelAcceso) {
        try {
            List<ClienteActividadDTO> actividad = clienteTimelineService.obtenerActividad(clienteId, nivelAcceso);
            return ResponseEntity.ok(actividad);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/notas")
    public ResponseEntity<?> obtenerNotas(@PathVariable Integer clienteId) {
        try {
            List<ClienteNotaDTO> notas = clienteTimelineService.obtenerNotasFijadas(clienteId);
            return ResponseEntity.ok(notas);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/notas")
    public ResponseEntity<?> crearNota(@PathVariable Integer clienteId,
            @Valid @RequestBody CrearNotaClienteRequest request) {
        try {
            ClienteNotaDTO nota = clienteTimelineService.crearNotaFijada(clienteId, request);
            return ResponseEntity.ok(nota);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/notas/{notaId}")
    public ResponseEntity<?> actualizarNota(@PathVariable Integer clienteId,
            @PathVariable Long notaId,
            @Valid @RequestBody ActualizarNotaClienteRequest request) {
        try {
            ClienteNotaDTO nota = clienteTimelineService.actualizarNota(clienteId, notaId, request);
            return ResponseEntity.ok(nota);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/notas/{notaId}")
    public ResponseEntity<?> eliminarNota(@PathVariable Integer clienteId,
            @PathVariable Long notaId) {
        try {
            clienteTimelineService.eliminarNota(clienteId, notaId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/incidencias")
    public ResponseEntity<?> obtenerIncidencias(@PathVariable Integer clienteId) {
        try {
            List<TareaDTO> incidencias = clienteTimelineService.obtenerIncidencias(clienteId);
            return ResponseEntity.ok(incidencias);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
