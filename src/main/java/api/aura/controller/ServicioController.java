package api.aura.controller;

import api.aura.entity.Servicio;
import api.aura.service.ServicioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servicios")
public class ServicioController {

    @Autowired
    private ServicioService servicioService;

    @PostMapping
    public ResponseEntity<Servicio> crearOActualizarServicio(@RequestBody Servicio servicio) {
        Servicio guardado = servicioService.guardarServicio(servicio);
        return ResponseEntity.ok(guardado);
    }

    @GetMapping
    public ResponseEntity<List<Servicio>> obtenerTodos() {
        List<Servicio> servicios = servicioService.obtenerTodos();
        return ResponseEntity.ok(servicios);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Servicio> obtenerPorId(@PathVariable Integer id) {
        return servicioService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Servicio>> obtenerPorClienteId(@PathVariable Integer clienteId) {
        List<Servicio> servicios = servicioService.obtenerPorClienteId(clienteId);
        return ResponseEntity.ok(servicios);
    }

    @GetMapping("/punto-suministro/{puntoSuministroId}")
    public ResponseEntity<List<Servicio>> obtenerPorPuntoSuministroId(@PathVariable Integer puntoSuministroId) {
        List<Servicio> servicios = servicioService.obtenerPorPuntoSuministroId(puntoSuministroId);
        return ResponseEntity.ok(servicios);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPorId(@PathVariable Integer id) {
        servicioService.eliminarPorId(id);
        return ResponseEntity.noContent().build();
    }
}
