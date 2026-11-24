package api.aura.controller.mcas;

import api.aura.dto.mcas.McasPoliticaRetrocesionDTO;
import api.aura.service.mcas.McasRetrocesionPolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * Políticas de retrocesión (Configuración MCAS › Retrocesiones). Informativas:
 * se muestran como «retrocesión potencial» y nunca se descuentan en el alta.
 * Solo nivel 1: la ruta la cubre el interceptor de /api/mcas/**.
 */
@RestController
@RequestMapping("/api/mcas/politicas-retrocesion")
public class McasPoliticasRetrocesionController {

    private final McasRetrocesionPolicyService service;

    public McasPoliticasRetrocesionController(McasRetrocesionPolicyService service) {
        this.service = service;
    }

    /** Todas, con sus tramos y el resumen en lenguaje natural. */
    @GetMapping
    public ResponseEntity<List<McasPoliticaRetrocesionDTO>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    /** Crea en BORRADOR. */
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody McasPoliticaRetrocesionDTO datos) {
        return responder(() -> service.crear(datos));
    }

    /** Solo BORRADOR; los tramos se sustituyen. */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody McasPoliticaRetrocesionDTO datos) {
        return responder(() -> service.actualizar(id, datos));
    }

    /** Solo BORRADOR y sin propuestas que la referencien. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        return responder(() -> { service.eliminar(id); return null; });
    }

    @PostMapping("/{id}/publicar")
    public ResponseEntity<?> publicar(@PathVariable Integer id) {
        return responder(() -> service.publicar(id));
    }

    @PostMapping("/{id}/archivar")
    public ResponseEntity<?> archivar(@PathVariable Integer id) {
        return responder(() -> service.archivar(id));
    }

    private ResponseEntity<?> responder(Supplier<Object> accion) {
        try {
            Object resultado = accion.get();
            return resultado == null ? ResponseEntity.ok().build() : ResponseEntity.ok(resultado);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
