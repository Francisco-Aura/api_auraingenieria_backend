package api.aura.controller.modulo_planificador;

import api.aura.service.modulo_planificador.PreciosReguladosService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Peajes y cargos del BOE de los estudios: se leen todos, se añade un BOE nuevo y se guarda o elimina uno entero. */
@RestController
@RequestMapping("/api/planificador/regulated-prices")
@RequiredArgsConstructor
public class PreciosReguladosController {

    private final PreciosReguladosService precios;

    @GetMapping
    public Map<String, Object> listar() {
        return precios.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crear(@RequestBody Map<String, Object> cuerpo) {
        return precios.crear(cuerpo);
    }

    @PutMapping("/{id}")
    public Map<String, Object> guardar(@PathVariable int id, @RequestBody Map<String, Object> cuerpo) {
        return precios.guardar(id, cuerpo);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> eliminar(@PathVariable int id) {
        return precios.eliminar(id);
    }
}
