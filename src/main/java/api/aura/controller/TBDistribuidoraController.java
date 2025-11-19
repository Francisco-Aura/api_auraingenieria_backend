package api.aura.controller;

import api.aura.entity.Distribuidora;
import api.aura.repository.DistribuidoraRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/distribuidoras")
public class TBDistribuidoraController {

    @Autowired
    private DistribuidoraRepository distribuidoraRepository;

    @GetMapping
    public ResponseEntity<List<Distribuidora>> obtenerTodas() {
        List<Distribuidora> distribuidoras = distribuidoraRepository.findAll();
        return ResponseEntity.ok(distribuidoras);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Distribuidora> obtenerPorId(@PathVariable Integer id) {
        return distribuidoraRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
} 