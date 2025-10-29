package api.aura.controller;

import api.aura.dto.PuntosSuministrosCarteraDTO;
import api.aura.entity.PuntoSuministro;
import api.aura.service.PuntoSuministroService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/puntos-suministro")
public class PuntoSuministroController {

    @Autowired
    private PuntoSuministroService puntoSuministroService;

    @PostMapping
    public ResponseEntity<?> crearOActualizarPuntoSuministro(@Valid @RequestBody PuntoSuministro puntoSuministro) {
        try {
            // *** NORMALIZACIÓN ROBUSTA DE CUPS EN EL CONTROLADOR ***
            System.out.println("=== DEBUG CONTROLADOR ===");
            System.out.println("CUPSluz recibido: '" + puntoSuministro.getCUPSluz() + "'");
            System.out.println("CUPSgas recibido: '" + puntoSuministro.getCUPSgas() + "'");

            // Normalizar CUPS antes de enviar al servicio
            puntoSuministro.setCUPSluz(normalizarCupsEnControlador(puntoSuministro.getCUPSluz()));
            puntoSuministro.setCUPSgas(normalizarCupsEnControlador(puntoSuministro.getCUPSgas()));

            System.out.println("CUPSluz normalizado en controlador: '" + puntoSuministro.getCUPSluz() + "'");
            System.out.println("CUPSgas normalizado en controlador: '" + puntoSuministro.getCUPSgas() + "'");

            PuntoSuministro guardado = puntoSuministroService.guardarPuntoSuministro(puntoSuministro);
            return ResponseEntity.ok(guardado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Error de validación",
                    "mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Error interno del servidor",
                    "mensaje", e.getMessage()));
        }
    }

    /**
     * Normaliza valores CUPS en el controlador para manejar strings "null", vacíos,
     * etc.
     */
    private String normalizarCupsEnControlador(String cups) {
        if (cups == null) {
            return null;
        }

        String trimmed = cups.trim();

        // Si es string vacío, literalmente "null", o "undefined", devolver null
        if (trimmed.isEmpty() || "null".equals(trimmed) || "undefined".equals(trimmed)) {
            return null;
        }

        return trimmed;
    }

    @GetMapping
    public ResponseEntity<List<PuntoSuministro>> obtenerTodos() {
        List<PuntoSuministro> puntos = puntoSuministroService.obtenerTodos();
        return ResponseEntity.ok(puntos);
    }

    @GetMapping("/all")
    public ResponseEntity<List<PuntosSuministrosCarteraDTO>> obtenerCarteraPuntosSuministro() {
        List<PuntosSuministrosCarteraDTO> puntos = puntoSuministroService.obtenerCarteraPuntoSuministro();

        return ResponseEntity.ok(puntos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PuntoSuministro> obtenerPorId(@PathVariable Integer id) {
        return puntoSuministroService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<PuntoSuministro>> obtenerPorClienteId(@PathVariable Integer clienteId) {
        List<PuntoSuministro> puntos = puntoSuministroService.obtenerPorClienteId(clienteId);
        return ResponseEntity.ok(puntos);
    }

    @GetMapping("/cliente/{clienteId}/cartera")
    public ResponseEntity<List<PuntosSuministrosCarteraDTO>> obtenerCarteraPuntosSuministroPorCliente(
            @PathVariable Integer clienteId) {
        List<PuntosSuministrosCarteraDTO> puntos = puntoSuministroService
                .obtenerCarteraPuntoSuministroPorCliente(clienteId);
        return ResponseEntity.ok(puntos);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPorId(@PathVariable Integer id) {
        puntoSuministroService.eliminarPorId(id);
        return ResponseEntity.noContent().build();
    }
}
