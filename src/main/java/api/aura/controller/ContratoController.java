package api.aura.controller;

import api.aura.entity.Contrato;
import api.aura.service.ContratoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contratos")
public class ContratoController {

    @Autowired
    private ContratoService contratoService;

    @PostMapping
    public ResponseEntity<Contrato> crearOActualizarContrato(@RequestBody Contrato contrato) {
        Contrato guardado = contratoService.guardarContrato(contrato);
        return ResponseEntity.ok(guardado);
    }

    @GetMapping
    public ResponseEntity<List<Contrato>> obtenerTodos() {
        List<Contrato> contratos = contratoService.obtenerTodos();
        return ResponseEntity.ok(contratos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Contrato> obtenerPorId(@PathVariable Integer id) {
        return contratoService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Contrato>> obtenerPorClienteId(@PathVariable Integer clienteId) {
        List<Contrato> contratos = contratoService.obtenerPorClienteId(clienteId);
        return ResponseEntity.ok(contratos);
    }

    @GetMapping("/punto-suministro/{puntoSuministroId}")
    public ResponseEntity<List<Contrato>> obtenerPorPuntoSuministroId(@PathVariable Integer puntoSuministroId) {
        List<Contrato> contratos = contratoService.obtenerPorPuntoSuministroId(puntoSuministroId);
        return ResponseEntity.ok(contratos);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPorId(@PathVariable Integer id) {
        contratoService.eliminarPorId(id);
        return ResponseEntity.noContent().build();
    }
}
