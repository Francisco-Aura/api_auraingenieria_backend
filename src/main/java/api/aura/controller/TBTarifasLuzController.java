package api.aura.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import api.aura.entity.TBTarifasLuz;
import api.aura.service.TBTarifasLuzService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tarifasLuz")
public class TBTarifasLuzController {

    @Autowired
    private TBTarifasLuzService tarifasLuzService;

    @PostMapping
    public ResponseEntity<TBTarifasLuz> crearTarifaLuz(@RequestBody TBTarifasLuz tarifaLuz) {
        return ResponseEntity.ok(tarifasLuzService.crearTarifaLuz(tarifaLuz));
    }

    @GetMapping
    public List<TBTarifasLuz> obtenerTodasLasTarifasLuz() {
        return tarifasLuzService.obtenerTodasLasTarifasLuz();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<TBTarifasLuz>> obtenerTarifaLuzPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(tarifasLuzService.obtenerTarifaLuzPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TBTarifasLuz> actualizarTarifaLuz(@PathVariable Integer id, @RequestBody TBTarifasLuz tarifaLuzActualizada) {
        return ResponseEntity.ok(tarifasLuzService.actualizarTarifaLuz(id, tarifaLuzActualizada));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarTarifaLuz(@PathVariable Integer id) {
        tarifasLuzService.eliminarTarifaLuz(id);
        return ResponseEntity.noContent().build();
    }
}
