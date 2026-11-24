package api.aura.controller.modulo_planificador;

import api.aura.service.modulo_planificador.ProyectoComercialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Proyectos comerciales (la fila de la lista «Presupuestos y estudios»), creados antes que sus estudios y presupuestos. */
@RestController
@RequestMapping("/api/planificador/commercial-projects")
@RequiredArgsConstructor
public class ProyectoComercialController {

    private final ProyectoComercialService proyectos;

    @GetMapping
    public List<Map<String, Object>> listar() {
        return proyectos.listar();
    }

    @GetMapping("/{id}")
    public Map<String, Object> obtener(@PathVariable String id) {
        return proyectos.obtener(id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> actualizar(@PathVariable String id, @RequestBody Map<String, Object> cuerpo) {
        return proyectos.actualizar(id, cuerpo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crear(@RequestBody Map<String, Object> cuerpo) {
        return proyectos.crear(cuerpo);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> eliminar(@PathVariable String id) {
        return proyectos.eliminar(id);
    }
}
