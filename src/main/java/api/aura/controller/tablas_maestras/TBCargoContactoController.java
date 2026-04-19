package api.aura.controller.tablas_maestras;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import api.aura.entity.tablas_maestras.TBCargoContacto;
import api.aura.service.tablas_maestras.TBCargoContactoService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/cargos-contacto")
public class TBCargoContactoController {

    @Autowired
    private TBCargoContactoService cargoContactoService;

    @PostMapping
    public ResponseEntity<TBCargoContacto> crear(@RequestBody TBCargoContacto cargo) {
        return ResponseEntity.ok(cargoContactoService.crear(cargo));
    }

    @GetMapping
    public List<TBCargoContacto> obtenerTodos() {
        return cargoContactoService.obtenerTodos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<TBCargoContacto>> obtenerPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(cargoContactoService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TBCargoContacto> actualizar(@PathVariable Integer id, @RequestBody TBCargoContacto cargoActualizado) {
        return ResponseEntity.ok(cargoContactoService.actualizar(id, cargoActualizado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        cargoContactoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
