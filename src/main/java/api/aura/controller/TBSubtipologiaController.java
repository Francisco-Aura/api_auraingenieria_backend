package api.aura.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import api.aura.entity.TBSubtipologia;
import api.aura.service.TBSubtipologiaService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/subtipologias")
public class TBSubtipologiaController {

    @Autowired
    private TBSubtipologiaService subtipologiaService;

    @PostMapping
    public ResponseEntity<TBSubtipologia> crearSubtipologia(@RequestBody TBSubtipologia subtipologia) {
        return ResponseEntity.ok(subtipologiaService.crearSubtipologia(subtipologia));
    }

    @GetMapping
    public List<TBSubtipologia> obtenerTodasLasSubtipologias() {
        return subtipologiaService.obtenerTodasLasSubtipologias();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<TBSubtipologia>> obtenerSubtipologiaPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(subtipologiaService.obtenerSubtipologiaPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TBSubtipologia> actualizarSubtipologia(@PathVariable Integer id, @RequestBody TBSubtipologia subtipologiaActualizada) {
        return ResponseEntity.ok(subtipologiaService.actualizarSubtipologia(id, subtipologiaActualizada));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarSubtipologia(@PathVariable Integer id) {
        subtipologiaService.eliminarSubtipologia(id);
        return ResponseEntity.noContent().build();
    }
}
