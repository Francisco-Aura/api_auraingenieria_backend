package api.aura.controller.modulo_planificador;

import api.aura.service.modulo_planificador.DocumentosService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Dropbox (carpetas de proyectos) y funciones encendidas del servidor. */
@RestController
@RequestMapping("/api/planificador")
@RequiredArgsConstructor
public class DocumentosController {

    private final DocumentosService documentos;

    @GetMapping("/features")
    public Map<String, Object> funciones() {
        return documentos.funciones();
    }

    @GetMapping("/dropbox")
    public Map<String, Object> estado() {
        return documentos.estado();
    }

    @GetMapping("/dropbox/folders")
    public Map<String, Object> carpetas(@RequestParam(defaultValue = "") String path) {
        return documentos.carpetas(path);
    }

    @GetMapping("/dropbox/preview")
    public Map<String, Object> vistaPrevia(@RequestParam String name, @RequestParam("start_date") String fechaInicio) {
        return documentos.vistaPrevia(name, fechaInicio);
    }

    @PostMapping("/projects/{id}/dropbox-folder")
    public Map<String, Object> crearCarpeta(@PathVariable String id, @RequestBody Map<String, Object> cuerpo) {
        return documentos.crearCarpeta(id, cuerpo);
    }

    @PostMapping("/projects/{id}/dropbox-folder/subfolders")
    public Map<String, Object> completarSubcarpetas(@PathVariable String id) {
        return documentos.completarSubcarpetas(id);
    }
}
