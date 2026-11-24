package api.aura.controller.tablas_maestras;

import api.aura.controller.SesionActual;
import api.aura.entity.tablas_maestras.TBAppMonitorizacion;
import api.aura.repository.tablas_maestras.TBAppMonitorizacionRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/app-monitorizacion")
public class TBAppMonitorizacionController {

    @Autowired
    private TBAppMonitorizacionRepository appMonitorizacionRepository;

    @GetMapping
    public List<TBAppMonitorizacion> obtenerTodos() {
        return appMonitorizacionRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TBAppMonitorizacion> obtenerPorId(@PathVariable Long id) {
        Optional<TBAppMonitorizacion> appMonitorizacion = appMonitorizacionRepository.findById(id);
        return appMonitorizacion.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TBAppMonitorizacion> crear(@RequestBody TBAppMonitorizacion appMonitorizacion) {
        SesionActual.exigirAdmin();
        // Sin try/catch: un dato que no cabe o repetido lo explica GlobalExceptionHandler (409)
        TBAppMonitorizacion nueva = appMonitorizacionRepository.save(appMonitorizacion);
        return new ResponseEntity<>(nueva, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TBAppMonitorizacion> actualizar(@PathVariable Long id,
            @RequestBody TBAppMonitorizacion appMonitorizacion) {
        SesionActual.exigirAdmin();
        Optional<TBAppMonitorizacion> datos = appMonitorizacionRepository.findById(id);
        if (datos.isPresent()) {
            TBAppMonitorizacion existente = datos.get();
            existente.setNombre(appMonitorizacion.getNombre());
            existente.setDescripcion(appMonitorizacion.getDescripcion());
            return new ResponseEntity<>(appMonitorizacionRepository.save(existente), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> eliminar(@PathVariable Long id) {
        SesionActual.exigirAdmin();
        // Si algún punto de suministro lo usa, GlobalExceptionHandler responde 409 explicándolo
        appMonitorizacionRepository.deleteById(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
