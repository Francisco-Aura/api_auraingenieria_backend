package api.aura.controller.tablas_maestras;

import api.aura.controller.SesionActual;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import api.aura.entity.tablas_maestras.TBTarifasLuz;
import api.aura.service.tablas_maestras.TBTarifasLuzService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tarifas-luz")
public class TBTarifasLuzController {

    @Autowired
    private TBTarifasLuzService tarifasLuzService;

    @PostMapping
    public ResponseEntity<TBTarifasLuz> crearTarifaLuz(@RequestBody TBTarifasLuz tarifaLuz) {
        SesionActual.exigirAdmin();
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
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(tarifasLuzService.actualizarTarifaLuz(id, tarifaLuzActualizada));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarTarifaLuz(@PathVariable Integer id) {
        SesionActual.exigirAdmin();
        tarifasLuzService.eliminarTarifaLuz(id);
        return ResponseEntity.noContent().build();
    }
}
