package api.aura.controller.modulo_tareas;

import api.aura.dto.modulo_tareas.ComentarioDTO;
import api.aura.service.modulo_tareas.TareaComentarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comentarios-tareas")
@RequiredArgsConstructor
public class TareaComentarioController {

    private final TareaComentarioService comentarioService;

    @GetMapping("/tarea/{tareaId}")
    public List<ComentarioDTO> obtenerPorTarea(@PathVariable Long tareaId) {
        return comentarioService.obtenerPorTarea(tareaId);
    }

    @PostMapping
    public ComentarioDTO crear(@RequestBody ComentarioDTO dto) {
        return comentarioService.crearComentario(dto);
    }

    @PutMapping("/{id}")
    public ComentarioDTO actualizar(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return comentarioService.actualizarComentario(id, body.get("comentario"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        comentarioService.eliminarComentario(id);
        return ResponseEntity.noContent().build();
    }
}
