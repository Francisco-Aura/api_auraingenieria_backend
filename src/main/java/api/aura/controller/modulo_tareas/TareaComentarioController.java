package api.aura.controller.modulo_tareas;

import api.aura.dto.modulo_tareas.ComentarioDTO;
import api.aura.service.modulo_tareas.TareaComentarioService;
import api.aura.service.modulo_tareas.FileStorageService;
import api.aura.entity.modulo_tareas.mt_Adjunto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comentarios-tareas")
@RequiredArgsConstructor
public class TareaComentarioController {

    private final TareaComentarioService comentarioService;
    private final FileStorageService fileStorageService;

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

    @PostMapping("/{comentarioId}/adjuntos")
    public ResponseEntity<Map<String, Object>> subirAdjunto(
            @PathVariable Long comentarioId,
            @RequestParam("file") MultipartFile file) {
        try {
            mt_Adjunto adjunto = comentarioService.agregarAdjunto(comentarioId, file);

            Map<String, Object> response = new HashMap<>();
            response.put("id", adjunto.getId());
            response.put("nombreArchivo", adjunto.getNombreArchivo());
            response.put("tamanio", adjunto.getTamanio());
            response.put("tipoMime", adjunto.getTipoMime());
            response.put("fechaSubida", adjunto.getFechaSubida());

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/adjuntos/{adjuntoId}")
    public ResponseEntity<Resource> descargarAdjunto(@PathVariable Long adjuntoId) {
        try {
            mt_Adjunto adjunto = comentarioService.obtenerAdjunto(adjuntoId);
            Path filePath = fileStorageService.cargarArchivo(adjunto.getRutaArchivo());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(adjunto.getTipoMime()))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + adjunto.getNombreArchivo() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/adjuntos/{adjuntoId}")
    public ResponseEntity<Void> eliminarAdjunto(@PathVariable Long adjuntoId) {
        try {
            comentarioService.eliminarAdjunto(adjuntoId);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
