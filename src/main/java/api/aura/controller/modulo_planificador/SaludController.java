package api.aura.controller.modulo_planificador;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/planificador")
public class SaludController {

    /** Mismo JSON que GET /api/health del Planificador actual. */
    @GetMapping("/health")
    public Map<String, String> salud() {
        return Map.of("status", "ok");
    }
}
