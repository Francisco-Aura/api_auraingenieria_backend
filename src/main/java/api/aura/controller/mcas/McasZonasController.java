package api.aura.controller.mcas;

import api.aura.entity.mcas.McasZonaComision;
import api.aura.service.mcas.McasCondicionesCobroService;
import api.aura.service.mcas.McasZonasService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Zonas de comisión de cada comercializadora en Configuración MCAS: qué provincias forman cada
 * zona. Solo administradores (McasAccessInterceptor).
 */
@RestController
@RequestMapping("/api/mcas/zonas")
public class McasZonasController {

    private final McasZonasService servicio;
    private final McasCondicionesCobroService pendientes;

    public McasZonasController(McasZonasService servicio, McasCondicionesCobroService pendientes) {
        this.servicio = servicio;
        this.pendientes = pendientes;
    }

    /** Lo guardado y cuántas comisiones pendientes de aprobar se calcularon con las zonas de antes. */
    public record Guardado(McasZonaComision zona, long pendientes) {}

    @GetMapping
    public List<McasZonaComision> listar() {
        return servicio.listar();
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody McasZonaComision datos) {
        try {
            return ResponseEntity.ok(conPendientes(servicio.crear(datos)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody McasZonaComision datos) {
        try {
            return ResponseEntity.ok(conPendientes(servicio.actualizar(id, datos)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            McasZonaComision borrada = servicio.eliminar(id);
            return ResponseEntity.ok(Map.of("ok", true, "pendientes",
                    pendientes.comisionesPendientesAfectadas(borrada.getIdComercializadora(), null)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Guardado conPendientes(McasZonaComision z) {
        return new Guardado(z, pendientes.comisionesPendientesAfectadas(z.getIdComercializadora(), null));
    }
}
