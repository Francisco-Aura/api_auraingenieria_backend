package api.aura.controller;

import api.aura.entity.TBPropiedad;
import api.aura.repository.TBPropiedadRepository;
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