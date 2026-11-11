package api.aura.controller.modulo_planificador;

import api.aura.service.modulo_planificador.ProveedorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/planificador/suppliers")
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService proveedorService;

    @GetMapping
    public List<Map<String, Object>> listar() {
        return proveedorService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crear(@RequestBody Map<String, Object> cuerpo) {
        Object nombre = cuerpo.get("name");
        return proveedorService.crear(nombre == null ? "" : String.valueOf(nombre));
    }

    @DeleteMapping("/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String nombre) {
        proveedorService.eliminar(nombre);
    }
}
