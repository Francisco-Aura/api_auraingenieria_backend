package api.aura.controller;

import api.aura.dto.PuntosSuministrosCarteraDTO;
import api.aura.entity.PuntoSuministro;
import api.aura.service.PuntoSuministroService;
import api.aura.util.CupsNormalizer;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/puntos-suministro")
public class PuntoSuministroController {

    @Autowired
    private PuntoSuministroService puntoSuministroService;

    @PostMapping
    public ResponseEntity<?> crearOActualizarPuntoSuministro(@Valid @RequestBody PuntoSuministro puntoSuministro) {
        // Normalizar CUPS antes de enviar al servicio
        puntoSuministro.setCUPSluz(CupsNormalizer.normalizar(puntoSuministro.getCUPSluz()));
        puntoSuministro.setCUPSgas(CupsNormalizer.normalizar(puntoSuministro.getCUPSgas()));
        PuntoSuministro guardado = puntoSuministroService.guardarPuntoSuministro(puntoSuministro);
        return ResponseEntity.ok(guardado);
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
        SesionActual.exigirAdmin();
        puntoSuministroService.eliminarPorId(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cliente/{clienteId}/dados-de-baja")
    public ResponseEntity<List<PuntoSuministro>> obtenerDadosDeBajaPorClienteId(@PathVariable Integer clienteId) {
        return ResponseEntity.ok(puntoSuministroService.obtenerDadosDeBajaPorClienteId(clienteId));
    }

    @PutMapping("/{id}/reactivar")
    public ResponseEntity<Void> reactivarPuntoSuministro(@PathVariable Integer id) {
        puntoSuministroService.reactivarPuntoSuministro(id);
        return ResponseEntity.noContent().build();
    }
}
