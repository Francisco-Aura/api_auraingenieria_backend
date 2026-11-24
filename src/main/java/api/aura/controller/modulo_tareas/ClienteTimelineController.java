package api.aura.controller.modulo_tareas;

import api.aura.config.AuraUserHolder;
import api.aura.controller.DescargaSegura;
import api.aura.controller.SesionActual;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
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
            // Los eventos solo visibles para nivel 1 dependen del nivel real de la
            // sesión; el parámetro nivelAcceso se mantiene por compatibilidad pero se ignora
            List<ClienteActividadDTO> actividad = clienteTimelineService.obtenerActividad(clienteId,
                    AuraUserHolder.getNivelAcceso());
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

    @PostMapping(value = "/notas", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> crearNotaJson(@PathVariable Integer clienteId,
            @RequestBody @Valid CrearNotaClienteRequest request) {
        // El autor de la nota es el usuario de la sesión, no el usuarioId del cuerpo
        request.setUsuarioId(SesionActual.idRequerido());
        try {
            return ResponseEntity.ok(clienteTimelineService.crearNotaFijada(clienteId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping(value = "/notas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> crearNotaMultipart(@PathVariable Integer clienteId,
            @RequestPart(value = "datos") @Valid CrearNotaClienteRequest request,
            @RequestPart(value = "archivos", required = false) List<MultipartFile> archivos,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo) {
        request.setUsuarioId(SesionActual.idRequerido());
        try {
            List<MultipartFile> todos = combinarArchivos(archivos, archivo);
            if (!todos.isEmpty()) {
                return ResponseEntity.ok(clienteTimelineService.crearNotaFijadaConArchivos(clienteId, request, todos));
            }
            return ResponseEntity.ok(clienteTimelineService.crearNotaFijada(clienteId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping(value = "/notas/{notaId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> actualizarNotaJson(@PathVariable Integer clienteId,
            @PathVariable Long notaId,
            @RequestBody @Valid ActualizarNotaClienteRequest request) {
        try {
            return ResponseEntity.ok(clienteTimelineService.actualizarNota(clienteId, notaId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping(value = "/notas/{notaId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> actualizarNotaMultipart(@PathVariable Integer clienteId,
            @PathVariable Long notaId,
            @RequestPart(value = "datos") @Valid ActualizarNotaClienteRequest request,
            @RequestPart(value = "archivos", required = false) List<MultipartFile> archivos,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo) {
        try {
            List<MultipartFile> todos = combinarArchivos(archivos, archivo);
            if (!todos.isEmpty()) {
                return ResponseEntity.ok(clienteTimelineService.actualizarNotaConArchivos(clienteId, notaId, request, todos));
            }
            return ResponseEntity.ok(clienteTimelineService.actualizarNota(clienteId, notaId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Combina la lista nueva de archivos con el parámetro individual antiguo
     * (compatibilidad hacia atrás).
     */
    private List<MultipartFile> combinarArchivos(List<MultipartFile> archivos, MultipartFile archivo) {
        List<MultipartFile> todos = new ArrayList<>();
        if (archivos != null) {
            archivos.stream().filter(a -> a != null && !a.isEmpty()).forEach(todos::add);
        }
        if (archivo != null && !archivo.isEmpty()) {
            todos.add(archivo);
        }
        return todos;
    }

    @GetMapping("/notas/{notaId}/adjuntos/{adjuntoId}/descargar")
    public ResponseEntity<Resource> descargarAdjuntoNota(@PathVariable Integer clienteId,
            @PathVariable Long notaId,
            @PathVariable Long adjuntoId) {
        try {
            ClienteTimelineService.ClienteNotaAdjuntoInfo info =
                    clienteTimelineService.obtenerAdjuntoParaDescargaInfo(clienteId, notaId, adjuntoId);
            Resource resource = new PathResource(info.ruta());
            return ResponseEntity.ok()
                    .headers(DescargaSegura.cabeceras(info.nombreArchivo(), "archivo"))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/notas/{notaId}/adjuntos/{adjuntoId}")
    public ResponseEntity<?> eliminarAdjuntoDeNota(@PathVariable Integer clienteId,
            @PathVariable Long notaId,
            @PathVariable Long adjuntoId) {
        try {
            return ResponseEntity.ok(clienteTimelineService.eliminarAdjuntoNota(clienteId, notaId, adjuntoId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/notas/{notaId}/archivo")
    public ResponseEntity<?> eliminarArchivoDeNota(@PathVariable Integer clienteId,
            @PathVariable Long notaId) {
        try {
            clienteTimelineService.eliminarArchivoNota(clienteId, notaId);
            return ResponseEntity.ok(clienteTimelineService.obtenerNotaDto(clienteId, notaId));
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
            return ResponseEntity.ok()
                    .headers(DescargaSegura.cabeceras(nombreOriginal, "archivo"))
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
