package api.aura.controller.tablas_maestras;

import api.aura.entity.tablas_maestras.TBTipoComunicacion;
import api.aura.repository.tablas_maestras.TBTipoComunicacionRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tipos-comunicacion")
public class TBTipoComunicacionController {

    @Autowired
    private TBTipoComunicacionRepository tipoComunicacionRepository;

    @GetMapping
    public ResponseEntity<List<TBTipoComunicacion>> obtenerTodos() {
        List<TBTipoComunicacion> tipos = tipoComunicacionRepository.findAll();
        return ResponseEntity.ok(tipos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TBTipoComunicacion> obtenerPorId(@PathVariable Integer id) {
        return tipoComunicacionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
} 