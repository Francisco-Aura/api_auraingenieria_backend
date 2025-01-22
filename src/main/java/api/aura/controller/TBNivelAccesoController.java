package api.aura.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import api.aura.entity.TBNivelAcceso;
import api.aura.service.TBNivelAccesoService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/niveles-acceso")
public class TBNivelAccesoController {

    @Autowired
    private TBNivelAccesoService nivelAccesoService;

    @PostMapping
    public ResponseEntity<TBNivelAcceso> crearNivelAcceso(@RequestBody TBNivelAcceso nivelAcceso) {
        return ResponseEntity.ok(nivelAccesoService.crearNivelAcceso(nivelAcceso));
    }

    @GetMapping
    public List<TBNivelAcceso> obtenerTodosLosNivelesAcceso() {
        return nivelAccesoService.obtenerTodosLosNivelesAcceso();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<TBNivelAcceso>> obtenerNivelAccesoPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(nivelAccesoService.obtenerNivelAccesoPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TBNivelAcceso> actualizarNivelAcceso(@PathVariable Integer id, @RequestBody TBNivelAcceso nivelAccesoActualizado) {
        return ResponseEntity.ok(nivelAccesoService.actualizarNivelAcceso(id, nivelAccesoActualizado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarNivelAcceso(@PathVariable Integer id) {
        nivelAccesoService.eliminarNivelAcceso(id);
        return ResponseEntity.noContent().build();
    }
}
