package api.aura.controller;

import api.aura.entity.AppMonitorizacion;
import api.aura.repository.AppMonitorizacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/app-monitorizacion")
public class AppMonitorizacionController {

    @Autowired
    private AppMonitorizacionRepository appMonitorizacionRepository;

    @GetMapping
    public List<AppMonitorizacion> obtenerTodos() {
        return appMonitorizacionRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppMonitorizacion> obtenerPorId(@PathVariable Long id) {
        Optional<AppMonitorizacion> appMonitorizacion = appMonitorizacionRepository.findById(id);
        return appMonitorizacion.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AppMonitorizacion> crear(@RequestBody AppMonitorizacion appMonitorizacion) {
        try {
            AppMonitorizacion nueva = appMonitorizacionRepository.save(appMonitorizacion);
            return new ResponseEntity<>(nueva, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppMonitorizacion> actualizar(@PathVariable Long id,
            @RequestBody AppMonitorizacion appMonitorizacion) {
        Optional<AppMonitorizacion> datos = appMonitorizacionRepository.findById(id);
        if (datos.isPresent()) {
            AppMonitorizacion existente = datos.get();
            existente.setNombre(appMonitorizacion.getNombre());
            existente.setDescripcion(appMonitorizacion.getDescripcion());
            return new ResponseEntity<>(appMonitorizacionRepository.save(existente), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> eliminar(@PathVariable Long id) {
        try {
            appMonitorizacionRepository.deleteById(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
