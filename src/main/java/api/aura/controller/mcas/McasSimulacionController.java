package api.aura.controller.mcas;

import api.aura.service.mcas.McasSimulacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Prueba de un conjunto de reglas con un contrato, sin guardar nada. Como el
 * resto de /api/mcas, exige administrador (McasAccessInterceptor).
 */
@RestController
@RequestMapping("/api/mcas/simulaciones")
public class McasSimulacionController {

    private final McasSimulacionService simulacion;

    public McasSimulacionController(McasSimulacionService simulacion) {
        this.simulacion = simulacion;
    }

    @GetMapping
    public ResponseEntity<?> simular(@RequestParam Integer conjunto, @RequestParam String contrato) {
        try {
            return ResponseEntity.ok(simulacion.simular(conjunto, contrato));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
