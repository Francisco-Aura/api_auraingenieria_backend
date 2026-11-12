package api.aura.controller.tablas_maestras;

import api.aura.controller.SesionActual;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import api.aura.entity.tablas_maestras.TBTarifasGas;
import api.aura.service.tablas_maestras.TBTarifasGasService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tarifas-gas")
public class TBTarifasGasController {

    @Autowired
    private TBTarifasGasService tarifasGasService;

    @PostMapping
    public ResponseEntity<TBTarifasGas> crearTarifaGas(@RequestBody TBTarifasGas tarifaGas) {
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(tarifasGasService.crearTarifaGas(tarifaGas));
    }

    @GetMapping
    public List<TBTarifasGas> obtenerTodasLasTarifasGas() {
        return tarifasGasService.obtenerTodasLasTarifasGas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<TBTarifasGas>> obtenerTarifaGasPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(tarifasGasService.obtenerTarifaGasPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TBTarifasGas> actualizarTarifaGas(@PathVariable Integer id, @RequestBody TBTarifasGas tarifaGasActualizada) {
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(tarifasGasService.actualizarTarifaGas(id, tarifaGasActualizada));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarTarifaGas(@PathVariable Integer id) {
        SesionActual.exigirAdmin();
        tarifasGasService.eliminarTarifaGas(id);
        return ResponseEntity.noContent().build();
    }
}
