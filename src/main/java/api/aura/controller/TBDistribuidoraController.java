package api.aura.controller;

import api.aura.entity.TBDistribuidora;
import api.aura.repository.TBDistribuidoraRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/distribuidoras")
public class TBDistribuidoraController {

    @Autowired
    private TBDistribuidoraRepository distribuidoraRepository;

    @GetMapping
    public ResponseEntity<List<TBDistribuidora>> obtenerTodas() {
        List<TBDistribuidora> distribuidoras = distribuidoraRepository.findAll();
        return ResponseEntity.ok(distribuidoras);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TBDistribuidora> obtenerPorId(@PathVariable Integer id) {
        return distribuidoraRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
} 