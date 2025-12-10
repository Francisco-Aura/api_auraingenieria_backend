package api.aura.controller.tablas_maestras;

import api.aura.entity.tablas_maestras.TBPropiedad;
import api.aura.repository.tablas_maestras.TBPropiedadRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/propiedades")
public class TBPropiedadController {

    @Autowired
    private TBPropiedadRepository propiedadRepository;

    @GetMapping
    public ResponseEntity<List<TBPropiedad>> obtenerTodas() {
        List<TBPropiedad> propiedades = propiedadRepository.findAll();
        return ResponseEntity.ok(propiedades);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TBPropiedad> obtenerPorId(@PathVariable Integer id) {
        return propiedadRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
} 