package api.aura.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import api.aura.entity.Incidencia;
import api.aura.service.IncidenciaService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/incidencias")
public class IncidenciaController {

    @Autowired
    private IncidenciaService incidenciaService;

    @PostMapping
    public ResponseEntity<Incidencia> crearIncidencia(@RequestBody Incidencia incidencia) {
        return ResponseEntity.ok(incidenciaService.crearIncidencia(incidencia));
    }

    @GetMapping
    public List<Incidencia> obtenerTodasLasIncidencias() {
        return incidenciaService.obtenerTodasLasIncidencias();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<Incidencia>> obtenerIncidenciaPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(incidenciaService.obtenerIncidenciaPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Incidencia> actualizarIncidencia(@PathVariable Integer id, @RequestBody Incidencia incidenciaActualizada) {
        return ResponseEntity.ok(incidenciaService.actualizarIncidencia(id, incidenciaActualizada));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarIncidencia(@PathVariable Integer id) {
        incidenciaService.eliminarIncidencia(id);
        return ResponseEntity.noContent().build();
    }
}
