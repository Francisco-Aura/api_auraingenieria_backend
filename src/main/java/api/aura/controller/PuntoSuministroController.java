package api.aura.controller;

import api.aura.dto.PuntosSuministrosCarteraDTO;
import api.aura.entity.PuntoSuministro;
import api.aura.service.PuntoSuministroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/puntos-suministro")
public class PuntoSuministroController {

    @Autowired
    private PuntoSuministroService puntoSuministroService;

    // Crear o actualizar un punto de suministro
    @PostMapping
    public ResponseEntity<PuntoSuministro> crearOActualizarPuntoSuministro(@RequestBody PuntoSuministro puntoSuministro) {
        PuntoSuministro guardado = puntoSuministroService.guardarPuntoSuministro(puntoSuministro);
        return ResponseEntity.ok(guardado);
    }

    // Obtener todos los puntos de suministro
    @GetMapping
    public ResponseEntity<List<PuntoSuministro>> obtenerTodos() {
        List<PuntoSuministro> puntos = puntoSuministroService.obtenerTodos();
        return ResponseEntity.ok(puntos);
    }

    // Obtener Cartera de los puntos de suministro en formato DTO
    @GetMapping("/all")
    public ResponseEntity<List<PuntosSuministrosCarteraDTO>> obtenerCarteraPuntosSuministro() {
        List<PuntosSuministrosCarteraDTO> puntos = puntoSuministroService.obtenerCarteraPuntoSuministro();
        return ResponseEntity.ok(puntos);
    }

    // Obtener un punto de suministro por ID
    @GetMapping("/{id}")
    public ResponseEntity<PuntoSuministro> obtenerPorId(@PathVariable Integer id) {
        return puntoSuministroService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Obtener puntos de suministro por cliente
    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<PuntoSuministro>> obtenerPorClienteId(@PathVariable Integer clienteId) {
        List<PuntoSuministro> puntos = puntoSuministroService.obtenerPorClienteId(clienteId);
        return ResponseEntity.ok(puntos);
    }

    // Eliminar un punto de suministro por ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPorId(@PathVariable Integer id) {
        puntoSuministroService.eliminarPorId(id);
        return ResponseEntity.noContent().build();
    }
}
