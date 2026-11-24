package api.aura.controller.tablas_maestras;

import api.aura.controller.SesionActual;
import api.aura.entity.tablas_maestras.TBIntegracionSistemasFV;
import api.aura.repository.tablas_maestras.TBIntegracionSistemasFVRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/integracion-sistemas-fv")
public class TBIntegracionSistemasFVController {

    @Autowired
    private TBIntegracionSistemasFVRepository TBIntegracionSistemasFVRepository;

    @GetMapping
    public List<TBIntegracionSistemasFV> obtenerTodos() {
        return TBIntegracionSistemasFVRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TBIntegracionSistemasFV> obtenerPorId(@PathVariable Long id) {
        Optional<TBIntegracionSistemasFV> integracion = TBIntegracionSistemasFVRepository.findById(id);
        return integracion.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TBIntegracionSistemasFV> crear(@RequestBody TBIntegracionSistemasFV integracion) {
        SesionActual.exigirAdmin();
        // Sin try/catch: un dato que no cabe o repetido lo explica GlobalExceptionHandler (409)
        TBIntegracionSistemasFV nueva = TBIntegracionSistemasFVRepository.save(integracion);
        return new ResponseEntity<>(nueva, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TBIntegracionSistemasFV> actualizar(@PathVariable Long id,
            @RequestBody TBIntegracionSistemasFV integracion) {
        SesionActual.exigirAdmin();
        Optional<TBIntegracionSistemasFV> datos = TBIntegracionSistemasFVRepository.findById(id);
        if (datos.isPresent()) {
            TBIntegracionSistemasFV existente = datos.get();
            existente.setNombre(integracion.getNombre());
            existente.setDescripcion(integracion.getDescripcion());
            return new ResponseEntity<>(TBIntegracionSistemasFVRepository.save(existente), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> eliminar(@PathVariable Long id) {
        SesionActual.exigirAdmin();
        // Si algún punto de suministro lo usa, GlobalExceptionHandler responde 409 explicándolo
        TBIntegracionSistemasFVRepository.deleteById(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
