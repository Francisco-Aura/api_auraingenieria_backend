package api.aura.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import api.aura.entity.TBMetodoPago;
import api.aura.service.TBMetodoPagoService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/metodos-pago")
public class TBMetodoPagoController {

    @Autowired
    private TBMetodoPagoService metodoPagoService;

    @PostMapping
    public ResponseEntity<TBMetodoPago> crearMetodoPago(@RequestBody TBMetodoPago metodoPago) {
        return ResponseEntity.ok(metodoPagoService.crearMetodoPago(metodoPago));
    }

    @GetMapping
    public List<TBMetodoPago> obtenerTodosLosMetodosPago() {
        return metodoPagoService.obtenerTodosLosMetodosPago();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<TBMetodoPago>> obtenerMetodoPagoPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(metodoPagoService.obtenerMetodoPagoPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TBMetodoPago> actualizarMetodoPago(@PathVariable Integer id, @RequestBody TBMetodoPago metodoPagoActualizado) {
        return ResponseEntity.ok(metodoPagoService.actualizarMetodoPago(id, metodoPagoActualizado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarMetodoPago(@PathVariable Integer id) {
        metodoPagoService.eliminarMetodoPago(id);
        return ResponseEntity.noContent().build();
    }
}
