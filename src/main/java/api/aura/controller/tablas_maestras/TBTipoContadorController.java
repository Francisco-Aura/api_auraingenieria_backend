package api.aura.controller.tablas_maestras;

import api.aura.entity.tablas_maestras.TBTipoContador;
import api.aura.repository.tablas_maestras.TBTipoContadorRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tipos-contador")
public class TBTipoContadorController {

    @Autowired
    private TBTipoContadorRepository tipoContadorRepository;

    @GetMapping
    public ResponseEntity<List<TBTipoContador>> obtenerTodos() {
        List<TBTipoContador> tipos = tipoContadorRepository.findAll();
        return ResponseEntity.ok(tipos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TBTipoContador> obtenerPorId(@PathVariable Integer id) {
        return tipoContadorRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
} 