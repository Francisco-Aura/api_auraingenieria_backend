package api.aura.controller;

import api.aura.entity.Servicio;
import api.aura.service.ServicioService;
import api.aura.dto.ServicioDTO;
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

    @PutMapping("/{id}")
    public ResponseEntity<Servicio> actualizarServicio(@PathVariable Integer id, @RequestBody Servicio servicio) {
        servicio.setId(id); // Ensure the ID is set correctly
        Servicio actualizado = servicioService.guardarServicio(servicio);
        return ResponseEntity.ok(actualizado);
    }

    @GetMapping
    public ResponseEntity<List<ServicioDTO>> obtenerTodos() {
        List<ServicioDTO> servicios = servicioService.obtenerTodos();
        return ResponseEntity.ok(servicios);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServicioDTO> obtenerPorId(@PathVariable Integer id) {
        return servicioService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<ServicioDTO>> obtenerPorClienteId(@PathVariable Integer clienteId) {
        List<ServicioDTO> servicios = servicioService.obtenerPorClienteId(clienteId);
        return ResponseEntity.ok(servicios);
    }

    @GetMapping("/punto-suministro/{puntoSuministroId}")
    public ResponseEntity<List<ServicioDTO>> obtenerPorPuntoSuministroId(@PathVariable Integer puntoSuministroId) {
        List<ServicioDTO> servicios = servicioService.obtenerPorPuntoSuministroId(puntoSuministroId);
        return ResponseEntity.ok(servicios);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPorId(@PathVariable Integer id) {
        servicioService.eliminarPorId(id);
        return ResponseEntity.noContent().build();
    }
}
