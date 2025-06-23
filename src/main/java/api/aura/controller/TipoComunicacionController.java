package api.aura.controller;

import api.aura.entity.TipoComunicacion;
import api.aura.repository.TipoComunicacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tipos-comunicacion")
public class TipoComunicacionController {

    @Autowired
    private TipoComunicacionRepository tipoComunicacionRepository;

    @GetMapping
    public ResponseEntity<List<TipoComunicacion>> obtenerTodos() {
        List<TipoComunicacion> tipos = tipoComunicacionRepository.findAll();
        return ResponseEntity.ok(tipos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoComunicacion> obtenerPorId(@PathVariable Integer id) {
        return tipoComunicacionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
} 