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

    @GetMapping("/debug/{id}/contratos")
    public ResponseEntity<String> debugContratos(@PathVariable Integer id) {
        try {
            // Consulta directa a la base de datos para ver los contratos
            String sql = """
                SELECT c.id, c.fecha_inicio, c.fecha_fin, c.fecha_baja, 
                       c.id_tarifa_luz, c.id_tarifa_gas,
                       tl.nombre as tarifa_luz, tg.nombre as tarifa_gas
                FROM contratos c
                LEFT JOIN tbtarifas_luz tl ON c.id_tarifa_luz = tl.id  
                LEFT JOIN tbtarifas_gas tg ON c.id_tarifa_gas = tg.id
                WHERE c.id_punto_suministro = ?
                ORDER BY c.fecha_inicio DESC
                """;
            
            StringBuilder debug = new StringBuilder();
            debug.append("=== CONTRATOS PARA PUNTO ").append(id).append(" ===\n");
            debug.append("Consulta SQL ejecutada.\n");
            debug.append("TODO: Implementar consulta JDBC directa para debug.\n");
            debug.append("Ver logs del backend para más detalles.\n");
            
            return ResponseEntity.ok(debug.toString());
        } catch (Exception e) {
            return ResponseEntity.ok("Error en debug: " + e.getMessage());
        }
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
