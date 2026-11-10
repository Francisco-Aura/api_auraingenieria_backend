package api.aura.controller.modulo_planificador;

import api.aura.service.modulo_planificador.TareaProyectoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/planificador")
@RequiredArgsConstructor
public class TareaProyectoController {

    private final TareaProyectoService tareaService;

    @GetMapping("/projects/{proyectoId}/tasks")
    public List<Map<String, Object>> listar(@PathVariable String proyectoId) {
        return tareaService.listar(proyectoId);
    }

    @PostMapping("/projects/{proyectoId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crear(@PathVariable String proyectoId, @RequestBody Map<String, Object> cuerpo) {
        return tareaService.crear(proyectoId, cuerpo);
    }

    @PatchMapping("/tasks/{id}")
    public Map<String, Object> actualizar(@PathVariable String id, @RequestBody Map<String, Object> cambios) {
        return tareaService.actualizar(id, new LinkedHashMap<>(cambios));
    }

    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String id) {
        tareaService.eliminar(id);
    }

    @PostMapping("/tasks/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> aportar(@PathVariable String id, @RequestBody Map<String, Object> cuerpo) {
        return tareaService.aportar(id, cuerpo);
    }

    @DeleteMapping("/tasks/{id}/attachments/{adjuntoId}")
    public Map<String, Object> quitarAdjunto(@PathVariable String id, @PathVariable String adjuntoId) {
        return tareaService.quitarAdjunto(id, adjuntoId);
    }

    // Los archivos van solo a Dropbox, a la carpeta del proyecto

    @PutMapping("/tasks/{id}/attachments/file")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> subirArchivo(@PathVariable String id, @RequestParam String name, @RequestParam(defaultValue = "") String requirement,
                                            @RequestHeader(value = "Content-Type", required = false) String tipo, @RequestBody(required = false) byte[] contenido) {
        return tareaService.subirArchivo(id, name, requirement, tipo, contenido == null ? new byte[0] : contenido);
    }

    @GetMapping("/tasks/{id}/attachments/{adjuntoId}/file")
    public ResponseEntity<Void> bajarArchivo(@PathVariable String id, @PathVariable String adjuntoId) {
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(URI.create(tareaService.enlaceDeArchivo(id, adjuntoId))).build();
    }
}
