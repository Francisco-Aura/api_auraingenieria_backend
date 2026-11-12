package api.aura.controller.tablas_maestras;

import api.aura.controller.SesionActual;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.service.tablas_maestras.TBPagoPlazosService;

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
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(pagoPlazosService.crearPagoPlazo(pagoPlazo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TBPagoPlazos> actualizarPagoPlazo(@PathVariable Integer id, @RequestBody TBPagoPlazos pagoPlazoActualizado) {
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(pagoPlazosService.actualizarPagoPlazo(id, pagoPlazoActualizado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPagoPlazo(@PathVariable Integer id) {
        SesionActual.exigirAdmin();
        pagoPlazosService.eliminarPagoPlazo(id);
        return ResponseEntity.noContent().build();
    }
}
