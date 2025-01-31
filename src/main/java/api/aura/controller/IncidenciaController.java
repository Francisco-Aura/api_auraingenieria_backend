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

    // Crear una nueva incidencia
    @PostMapping
    public ResponseEntity<Incidencia> crearIncidencia(@RequestBody Incidencia incidencia) {
        return ResponseEntity.ok(incidenciaService.crearIncidencia(incidencia));
    }

    // Obtener todas las incidencias
    @GetMapping
    public List<Incidencia> obtenerTodasLasIncidencias() {
        return incidenciaService.obtenerTodasLasIncidencias();
    }

    // Obtener una incidencia por ID
    @GetMapping("/{id}")
    public ResponseEntity<Optional<Incidencia>> obtenerIncidenciaPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(incidenciaService.obtenerIncidenciaPorId(id));
    }

    // Actualizar una incidencia existente
    @PutMapping("/{id}")
    public ResponseEntity<Incidencia> actualizarIncidencia(@PathVariable Integer id, @RequestBody Incidencia incidenciaActualizada) {
        return ResponseEntity.ok(incidenciaService.actualizarIncidencia(id, incidenciaActualizada));
    }

    // Eliminar una incidencia
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarIncidencia(@PathVariable Integer id) {
        incidenciaService.eliminarIncidencia(id);
        return ResponseEntity.noContent().build();
    }
}
