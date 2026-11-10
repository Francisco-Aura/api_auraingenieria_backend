package api.aura.controller.modulo_planificador;

import api.aura.service.modulo_planificador.BorradorService;
import api.aura.service.modulo_planificador.ContratoProyectoService;
import api.aura.service.modulo_planificador.MaterialService;
import api.aura.service.modulo_planificador.PlantillaService;
import api.aura.service.modulo_planificador.ProyectoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/planificador")
@RequiredArgsConstructor
public class ProyectoController {

    private final ProyectoService proyectoService;
    private final PlantillaService plantillaService;
    private final BorradorService borradorService;
    private final MaterialService materialService;

    @GetMapping("/templates")
    public List<Map<String, Object>> plantillas() {
        return plantillaService.listar();
    }

    // ------------------------------------------------------------------ proyectos

    @GetMapping("/projects")
    public List<Map<String, Object>> listar() {
        return proyectoService.listar();
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crear(@RequestBody Map<String, Object> cuerpo) {
        return proyectoService.crear(cuerpo);
    }

    @GetMapping("/projects/{id}")
    public Map<String, Object> obtener(@PathVariable String id) {
        return proyectoService.obtener(id);
    }

    @PatchMapping("/projects/{id}")
    public Map<String, Object> actualizar(@PathVariable String id, @RequestBody Map<String, Object> cambios) {
        return proyectoService.actualizar(id, new LinkedHashMap<>(cambios));
    }

    @PostMapping("/projects/{id}/apply-template")
    public Map<String, Object> aplicarPlantilla(@PathVariable String id, @RequestBody Map<String, Object> cuerpo) {
        return proyectoService.aplicarPlantilla(id, cuerpo);
    }

    @GetMapping("/projects/{id}/activity")
    public List<Map<String, Object>> actividad(@PathVariable String id, @RequestParam(defaultValue = "500") int limit) {
        return proyectoService.actividad(id, limit);
    }

    @GetMapping("/contract/fields")
    public List<Map<String, Object>> camposDelContrato() {
        return ContratoProyectoService.CAMPOS;
    }

    @GetMapping("/projects/{id}/contract")
    public Map<String, Object> contrato(@PathVariable String id) {
        return proyectoService.verContrato(id);
    }

    @GetMapping("/projects/{id}/budget")
    public Map<String, Object> presupuesto(@PathVariable String id) {
        return proyectoService.verPresupuesto(id);
    }

    @GetMapping("/projects/{id}/material")
    public Map<String, Object> material(@PathVariable String id) {
        return materialService.ver(id);
    }

    @PostMapping("/projects/{id}/material/returns")
    public Map<String, Object> devolverMaterial(@PathVariable String id, @RequestBody Map<String, Object> cuerpo) {
        return materialService.devolver(id, cuerpo);
    }

    @DeleteMapping("/projects/{id}/material/returns/{devolucionId}")
    public Map<String, Object> deshacerDevolucion(@PathVariable String id, @PathVariable String devolucionId) {
        return materialService.deshacer(id, devolucionId);
    }

    @DeleteMapping("/projects/{id}")
    public Map<String, Object> eliminar(@PathVariable String id, @RequestParam(defaultValue = "") String confirm) {
        return proyectoService.eliminar(id, confirm);
    }

    // ------------------------------------------------------------------ borradores

    @GetMapping("/drafts")
    public List<Map<String, Object>> borradores() {
        return borradorService.listar();
    }

    @GetMapping("/drafts/{id}")
    public Map<String, Object> borrador(@PathVariable String id) {
        return borradorService.obtener(id);
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crearBorrador(@RequestBody Map<String, Object> cuerpo) {
        return borradorService.crear(cuerpo);
    }

    @PutMapping("/drafts/{id}")
    public Map<String, Object> actualizarBorrador(@PathVariable String id, @RequestBody Map<String, Object> cuerpo) {
        return borradorService.actualizar(id, cuerpo);
    }

    @DeleteMapping("/drafts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarBorrador(@PathVariable String id) {
        borradorService.eliminar(id);
    }
}
