package api.aura.controller.tablas_maestras;

import api.aura.controller.SesionActual;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.service.tablas_maestras.TBComercializadorasService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/comercializadoras")
public class TBComercializadorasController {

    @Autowired
    private TBComercializadorasService comercializadorasService;

    @PostMapping
    public ResponseEntity<TBComercializadoras> crearComercializadora(
            @RequestBody TBComercializadoras comercializadora) {
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(comercializadorasService.crearComercializadora(comercializadora));
    }

    @GetMapping
    public List<TBComercializadoras> obtenerTodasLasComercializadoras() {
        return comercializadorasService.obtenerTodasLasComercializadoras();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<TBComercializadoras>> obtenerComercializadoraPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(comercializadorasService.obtenerComercializadoraPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TBComercializadoras> actualizarComercializadora(@PathVariable Integer id,
            @RequestBody TBComercializadoras comercializadoraActualizada) {
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(comercializadorasService.actualizarComercializadora(id, comercializadoraActualizada));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarComercializadora(@PathVariable Integer id) {
        SesionActual.exigirAdmin();
        comercializadorasService.eliminarComercializadora(id);
        return ResponseEntity.noContent().build();
    }
}
