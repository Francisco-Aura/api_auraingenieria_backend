package api.aura.controller.mcas;

import api.aura.entity.mcas.McasCondicionCobro;
import api.aura.service.mcas.McasCondicionesCobroService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Condiciones de cobro de cada comercializadora (y gestora) en Configuración MCAS: número de
 * pagos y fecha del primer cobro. Solo administradores (McasAccessInterceptor).
 */
@RestController
@RequestMapping("/api/mcas/condiciones-cobro")
public class McasCondicionesCobroController {

    private final McasCondicionesCobroService servicio;

    public McasCondicionesCobroController(McasCondicionesCobroService servicio) {
        this.servicio = servicio;
    }

    /** Lo guardado y cuántas comisiones pendientes de aprobar se calcularon con las condiciones de antes. */
    public record Guardado(McasCondicionCobro condiciones, long pendientes) {}

    @GetMapping
    public List<McasCondicionCobro> listar() {
        return servicio.listar();
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody McasCondicionCobro datos) {
        try {
            return ResponseEntity.ok(conPendientes(servicio.crear(datos)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody McasCondicionCobro datos) {
        try {
            return ResponseEntity.ok(conPendientes(servicio.actualizar(id, datos)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Guardado conPendientes(McasCondicionCobro c) {
        return new Guardado(c, servicio.comisionesPendientesAfectadas(c.getIdComercializadora(), c.getIdGestora()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            McasCondicionCobro borrada = servicio.eliminar(id);
            return ResponseEntity.ok(Map.of("ok", true, "pendientes",
                    servicio.comisionesPendientesAfectadas(borrada.getIdComercializadora(), borrada.getIdGestora())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
