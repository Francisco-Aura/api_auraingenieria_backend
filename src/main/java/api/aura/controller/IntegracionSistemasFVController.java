package api.aura.controller;

import api.aura.entity.IntegracionSistemasFV;
import api.aura.repository.IntegracionSistemasFVRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/integracion-sistemas-fv")
public class IntegracionSistemasFVController {

    @Autowired
    private IntegracionSistemasFVRepository integracionSistemasFVRepository;

    @GetMapping
    public List<IntegracionSistemasFV> obtenerTodos() {
        return integracionSistemasFVRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<IntegracionSistemasFV> obtenerPorId(@PathVariable Long id) {
        Optional<IntegracionSistemasFV> integracion = integracionSistemasFVRepository.findById(id);
        return integracion.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<IntegracionSistemasFV> crear(@RequestBody IntegracionSistemasFV integracion) {
        try {
            IntegracionSistemasFV nueva = integracionSistemasFVRepository.save(integracion);
            return new ResponseEntity<>(nueva, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<IntegracionSistemasFV> actualizar(@PathVariable Long id,
            @RequestBody IntegracionSistemasFV integracion) {
        Optional<IntegracionSistemasFV> datos = integracionSistemasFVRepository.findById(id);
        if (datos.isPresent()) {
            IntegracionSistemasFV existente = datos.get();
            existente.setNombre(integracion.getNombre());
            existente.setDescripcion(integracion.getDescripcion());
            return new ResponseEntity<>(integracionSistemasFVRepository.save(existente), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> eliminar(@PathVariable Long id) {
        try {
            integracionSistemasFVRepository.deleteById(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
