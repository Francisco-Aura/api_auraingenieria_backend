package api.aura.controller.mcas;

import api.aura.entity.mcas.McasRetrocesion;
import api.aura.service.mcas.McasRetrocesionService;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Retrocesiones que salen solas cuando un contrato comisionado se da de baja o cambia de
 * comercializadora antes del año. Se aprueban o descartan en Comisiones MCAS. Solo
 * administradores (McasAccessInterceptor).
 */
@RestController
@RequestMapping("/api/mcas/retrocesiones")
public class McasRetrocesionesController {

    private final McasRetrocesionService servicio;

    public McasRetrocesionesController(McasRetrocesionService servicio) {
        this.servicio = servicio;
    }

    public record Peticion(BigDecimal importe, String motivo) {}

    @GetMapping
    public List<McasRetrocesionService.Vista> listar() {
        return servicio.listar();
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<?> aprobar(@PathVariable Long id, @RequestBody(required = false) Peticion peticion) {
        return responder(() -> servicio.aprobar(id, peticion != null ? peticion.importe() : null));
    }

    @PostMapping("/{id}/descartar")
    public ResponseEntity<?> descartar(@PathVariable Long id, @RequestBody(required = false) Peticion peticion) {
        return responder(() -> servicio.descartar(id, peticion != null ? peticion.motivo() : null));
    }

    private ResponseEntity<?> responder(java.util.function.Supplier<McasRetrocesion> accion) {
        try {
            return ResponseEntity.ok(accion.get());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ObjectOptimisticLockingFailureException e) {
            return ResponseEntity.status(409).body(Map.of("error",
                    "Otra persona ha cambiado esta retrocesión a la vez: recargue la lista e inténtelo de nuevo."));
        }
    }
}
