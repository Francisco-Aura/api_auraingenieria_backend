package api.aura.controller.tablas_maestras;

import api.aura.controller.SesionActual;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import api.aura.entity.tablas_maestras.TBMetodoPago;
import api.aura.service.tablas_maestras.TBMetodoPagoService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/metodos-pago")
public class TBMetodoPagoController {

    @Autowired
    private TBMetodoPagoService metodoPagoService;

    @PostMapping
    public ResponseEntity<TBMetodoPago> crearMetodoPago(@RequestBody TBMetodoPago metodoPago) {
        SesionActual.exigirAdmin();
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
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(metodoPagoService.actualizarMetodoPago(id, metodoPagoActualizado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarMetodoPago(@PathVariable Integer id) {
        SesionActual.exigirAdmin();
        metodoPagoService.eliminarMetodoPago(id);
        return ResponseEntity.noContent().build();
    }
}
