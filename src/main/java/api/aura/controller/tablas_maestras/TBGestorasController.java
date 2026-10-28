package api.aura.controller.tablas_maestras;

import api.aura.controller.SesionActual;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import api.aura.entity.tablas_maestras.TBGestoras;
import api.aura.service.tablas_maestras.TBGestorasService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/gestoras")
public class TBGestorasController {

    @Autowired
    private TBGestorasService gestorasService;

    @PostMapping
    public ResponseEntity<TBGestoras> crearGestora(@RequestBody TBGestoras gestora) {
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(gestorasService.crearGestora(gestora));
    }

    @GetMapping
    public List<TBGestoras> obtenerTodasLasGestoras() {
        return gestorasService.obtenerTodasLasGestoras();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<TBGestoras>> obtenerGestoraPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(gestorasService.obtenerGestoraPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TBGestoras> actualizarGestora(@PathVariable Integer id, @RequestBody TBGestoras gestoraActualizada) {
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(gestorasService.actualizarGestora(id, gestoraActualizada));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarGestora(@PathVariable Integer id) {
        SesionActual.exigirAdmin();
        gestorasService.eliminarGestora(id);
        return ResponseEntity.noContent().build();
    }
}
