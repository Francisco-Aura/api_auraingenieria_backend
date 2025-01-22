package api.aura.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import api.aura.entity.TBComercializadoras;
import api.aura.service.TBComercializadorasService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/comercializadoras")
public class TBComercializadorasController {

    @Autowired
    private TBComercializadorasService comercializadorasService;

    // Crear un nuevo registro
    @PostMapping
    public ResponseEntity<TBComercializadoras> crearComercializadora(@RequestBody TBComercializadoras comercializadora) {
        return ResponseEntity.ok(comercializadorasService.crearComercializadora(comercializadora));
    }

    // Listar todos los registros
    @GetMapping
    public List<TBComercializadoras> obtenerTodasLasComercializadoras() {
        return comercializadorasService.obtenerTodasLasComercializadoras();
    }

    // Buscar por ID
    @GetMapping("/{id}")
    public ResponseEntity<Optional<TBComercializadoras>> obtenerComercializadoraPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(comercializadorasService.obtenerComercializadoraPorId(id));
    }

    // Actualizar un registro existente
    @PutMapping("/{id}")
    public ResponseEntity<TBComercializadoras> actualizarComercializadora(@PathVariable Integer id, @RequestBody TBComercializadoras comercializadoraActualizada) {
        return ResponseEntity.ok(comercializadorasService.actualizarComercializadora(id, comercializadoraActualizada));
    }

    // Eliminar un registro
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarComercializadora(@PathVariable Integer id) {
        comercializadorasService.eliminarComercializadora(id);
        return ResponseEntity.noContent().build();
    }
}
