package api.aura.controller.modulo_tareas;

import api.aura.dto.modulo_tareas.ClienteActividadDTO;
import api.aura.dto.modulo_tareas.ClienteNotaDTO;
import api.aura.dto.modulo_tareas.ActualizarNotaClienteRequest;
import api.aura.dto.modulo_tareas.CrearNotaClienteRequest;
import api.aura.dto.modulo_tareas.TareaDTO;
import api.aura.service.modulo_tareas.ClienteTimelineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
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

    @PostMapping(value = "/notas", consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<?> crearNota(@PathVariable Integer clienteId,
            @RequestPart(value = "datos", required = false) @Valid CrearNotaClienteRequest request,
            @RequestBody(required = false) CrearNotaClienteRequest requestBody,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo) {
        try {
            CrearNotaClienteRequest req = request != null ? request : requestBody;
            ClienteNotaDTO nota;
            if (archivo != null && !archivo.isEmpty()) {
                nota = clienteTimelineService.crearNotaFijadaConArchivo(clienteId, req, archivo);
            } else {
                nota = clienteTimelineService.crearNotaFijada(clienteId, req);
            }
            return ResponseEntity.ok(nota);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping(value = "/notas/{notaId}", consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<?> actualizarNota(@PathVariable Integer clienteId,
            @PathVariable Long notaId,
            @RequestPart(value = "datos", required = false) @Valid ActualizarNotaClienteRequest request,
            @RequestBody(required = false) ActualizarNotaClienteRequest requestBody,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo) {
        try {
            ActualizarNotaClienteRequest req = request != null ? request : requestBody;
            ClienteNotaDTO nota;
            if (archivo != null && !archivo.isEmpty()) {
                nota = clienteTimelineService.actualizarNotaConArchivo(clienteId, notaId, req, archivo);
            } else {
                nota = clienteTimelineService.actualizarNota(clienteId, notaId, req);
            }
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

    @GetMapping("/notas/{notaId}/descargar")
    public ResponseEntity<Resource> descargarArchivoNota(@PathVariable Integer clienteId,
            @PathVariable Long notaId) {
        try {
            Path ruta = clienteTimelineService.obtenerRutaArchivoNota(clienteId, notaId);
            String nombreOriginal = clienteTimelineService.obtenerNombreArchivoNota(clienteId, notaId);
            Resource resource = new PathResource(ruta);
            String contentDisposition = "attachment; filename=\"" +
                    (nombreOriginal != null ? nombreOriginal.replace("\"", "").replaceAll("[\\r\\n]", "") : "archivo") + "\"";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
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

    @PatchMapping("/notas/{notaId}/fijado")
    public ResponseEntity<?> toggleFijadoNota(@PathVariable Integer clienteId,
            @PathVariable Long notaId) {
        try {
            ClienteNotaDTO nota = clienteTimelineService.toggleFijadoNota(clienteId, notaId);
            return ResponseEntity.ok(nota);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
