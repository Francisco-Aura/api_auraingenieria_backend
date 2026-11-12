package api.aura.controller.modulo_tareas;

import lombok.extern.slf4j.Slf4j;
import api.aura.dto.modulo_tareas.ComentarioDTO;
import api.aura.controller.DescargaSegura;
import api.aura.controller.SesionActual;
import api.aura.config.AuraUserHolder;
import api.aura.service.modulo_tareas.TareaComentarioService;
import api.aura.service.modulo_tareas.TareaService;
import api.aura.service.modulo_tareas.FileStorageService;
import api.aura.entity.modulo_tareas.mt_Adjunto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/comentarios-tareas")
@RequiredArgsConstructor
public class TareaComentarioController {

    private final TareaComentarioService comentarioService;
    private final FileStorageService fileStorageService;
    private final TareaService tareaService;

    @GetMapping("/tarea/{tareaId}")
    public List<ComentarioDTO> obtenerPorTarea(@PathVariable Long tareaId) {
        exigirVerTarea(tareaId);
        return comentarioService.obtenerPorTarea(tareaId);
    }

    /** Comentarios y archivos de una tarea (leer y escribir): solo de las que el usuario puede ver. */
    private void exigirVerTarea(Long tareaId) {
        Integer usuario = SesionActual.idRequerido();
        if (!AuraUserHolder.esAdmin() && !tareaService.puedeVerTarea(tareaId, usuario)) {
            throw new SecurityException("No tiene acceso a esta tarea");
        }
    }

    @PostMapping
    public ComentarioDTO crear(@RequestBody ComentarioDTO dto) {
        // El autor es siempre el usuario de la sesión, no el usuarioId del cuerpo
        dto.setUsuarioId(SesionActual.idRequerido());
        exigirVerTarea(dto.getTareaId());
        return comentarioService.crearComentario(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            Object textoComentario = body.get("comentario");
            if (textoComentario != null && !(textoComentario instanceof String)) {
                return ResponseEntity.badRequest().body("El comentario no es válido");
            }
            String comentario = (String) textoComentario;
            // Solo el autor puede editar: se compara con el usuario de la sesión,
            // no con el usuarioId que envíe el cliente
            Integer usuarioId = SesionActual.idRequerido();

            ComentarioDTO resultado = comentarioService.actualizarComentario(id, comentario, usuarioId);
            return ResponseEntity.ok(resultado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error al actualizar comentario", e);
            return ResponseEntity.status(500).body("Error al actualizar comentario");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id,
            @RequestParam(required = false) Integer usuarioId) {
        // El permiso (solo administradores) se evalúa sobre el usuario de la sesión;
        // el parámetro usuarioId se mantiene por compatibilidad pero se ignora
        Integer usuarioSesion = SesionActual.idRequerido();
        try {
            comentarioService.eliminarComentario(id, usuarioSesion);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error al eliminar comentario", e);
            return ResponseEntity.status(500).body("Error al eliminar comentario");
        }
    }

    @PatchMapping("/{id}/fijado")
    public ComentarioDTO toggleFijado(@PathVariable Long id) {
        exigirVerTarea(comentarioService.tareaDelComentario(id));
        return comentarioService.toggleFijado(id);
    }

    @PostMapping("/{comentarioId}/adjuntos")
    public ResponseEntity<Map<String, Object>> subirAdjunto(
            @PathVariable Long comentarioId,
            @RequestParam("file") MultipartFile file) {
        exigirVerTarea(comentarioService.tareaDelComentario(comentarioId));
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
            log.error("Error de disco con un adjunto", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/adjuntos/{adjuntoId}")
    public ResponseEntity<Resource> descargarAdjunto(@PathVariable Long adjuntoId) {
        try {
            mt_Adjunto adjunto = comentarioService.obtenerAdjunto(adjuntoId);
            exigirVerTarea(comentarioService.tareaDelAdjunto(adjuntoId));
            Path filePath = fileStorageService.cargarArchivo(adjunto.getRutaArchivo());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                // Tipo declarado por quien subió el archivo: se neutralizan los
                // ejecutables por el navegador (HTML/SVG...) y se fuerza descarga
                return ResponseEntity.ok()
                        .headers(DescargaSegura.cabeceras(adjunto.getNombreArchivo(), "archivo"))
                        .contentType(DescargaSegura.tipoSeguro(adjunto.getTipoMime()))
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            log.error("Error de disco con un adjunto", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/adjuntos/{adjuntoId}")
    public ResponseEntity<Void> eliminarAdjunto(@PathVariable Long adjuntoId) {
        exigirVerTarea(comentarioService.tareaDelAdjunto(adjuntoId));
        try {
            comentarioService.eliminarAdjunto(adjuntoId);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            log.error("Error de disco con un adjunto", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/adjuntos/{adjuntoId}")
    public ResponseEntity<Map<String, Object>> actualizarAdjunto(
            @PathVariable Long adjuntoId,
            @RequestParam("file") MultipartFile file) {
        exigirVerTarea(comentarioService.tareaDelAdjunto(adjuntoId));
        try {
            mt_Adjunto adjunto = comentarioService.actualizarAdjunto(adjuntoId, file);

            Map<String, Object> response = new HashMap<>();
            response.put("id", adjunto.getId());
            response.put("nombreArchivo", adjunto.getNombreArchivo());
            response.put("tamanio", adjunto.getTamanio());
            response.put("tipoMime", adjunto.getTipoMime());
            response.put("fechaSubida", adjunto.getFechaSubida());

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Error de disco con un adjunto", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
