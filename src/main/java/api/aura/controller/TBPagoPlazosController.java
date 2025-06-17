package api.aura.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import api.aura.entity.TBPagoPlazos;
import api.aura.service.TBPagoPlazosService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/pagos-plazos")
public class TBPagoPlazosController {

    @Autowired
    private TBPagoPlazosService pagoPlazosService;

    @GetMapping
    public List<TBPagoPlazos> obtenerTodosLosPagosPlazos() {
        return pagoPlazosService.obtenerTodosLosPagosPlazos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<TBPagoPlazos>> obtenerPagoPlazoPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(pagoPlazosService.obtenerPagoPlazoPorId(id));
    }

    @PostMapping
    public ResponseEntity<TBPagoPlazos> crearPagoPlazo(@RequestBody TBPagoPlazos pagoPlazo) {
        return ResponseEntity.ok(pagoPlazosService.crearPagoPlazo(pagoPlazo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TBPagoPlazos> actualizarPagoPlazo(@PathVariable Integer id, @RequestBody TBPagoPlazos pagoPlazoActualizado) {
        return ResponseEntity.ok(pagoPlazosService.actualizarPagoPlazo(id, pagoPlazoActualizado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPagoPlazo(@PathVariable Integer id) {
        pagoPlazosService.eliminarPagoPlazo(id);
        return ResponseEntity.noContent().build();
    }
}
