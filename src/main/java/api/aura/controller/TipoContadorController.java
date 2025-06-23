package api.aura.controller;

import api.aura.entity.TipoContador;
import api.aura.repository.TipoContadorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tipos-contador")
public class TipoContadorController {

    @Autowired
    private TipoContadorRepository tipoContadorRepository;

    @GetMapping
    public ResponseEntity<List<TipoContador>> obtenerTodos() {
        List<TipoContador> tipos = tipoContadorRepository.findAll();
        return ResponseEntity.ok(tipos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoContador> obtenerPorId(@PathVariable Integer id) {
        return tipoContadorRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
} 