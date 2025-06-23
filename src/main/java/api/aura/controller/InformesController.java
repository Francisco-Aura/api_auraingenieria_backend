package api.aura.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import api.aura.dto.informes.*;
import api.aura.dto.ServicioPendientePagoDTO;
import api.aura.service.InformesService;


@RestController
@RequestMapping("/api/informes")
public class InformesController {
    private final InformesService servicio;

    public InformesController(InformesService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/contratos-pendientes-renovacion")
    public List<ContratoPendienteRenovacionDTO> obtenerContratosPendientesRenovacion() {
        return servicio.obtenerContratosPendientesRenovacion();
    }

    @GetMapping("/puntos-suministro-sin-contrato")
    public List<PuntosSuministroSinContratoDTO> obtenerPuntosSuministroSinContrato() {
        return servicio.obtenerPuntosSuministroSinContrato();
    }

    @PutMapping("/contratos-pendientes-renovacion/actualizar/{id}")
    public ResponseEntity<?> actualizarContratoPendienteRenovacion(@PathVariable Integer id, @RequestBody ContratoPendienteRenovacionDTO dto) {
        try {
            ContratoPendienteRenovacionDTO actualizado = servicio.actualizarContratoPendienteRenovacion(id, dto);
            return ResponseEntity.ok(Map.of("success", true, "data", actualizado));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/puntos-suministro-sin-contrato/actualizar/{id}")
    public ResponseEntity<?> actualizarPuntoSuministroSinContrato(@PathVariable String id, @RequestBody PuntosSuministroSinContratoDTO dto) {
        try {
            PuntosSuministroSinContratoDTO actualizado = servicio.actualizarPuntoSuministroSinContrato(id, dto);
            return ResponseEntity.ok(Map.of("success", true, "data", actualizado));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/servicios-pendientes-pago")
    public List<ServicioPendientePagoDTO> obtenerServiciosPendientesPago(
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fechaDesde,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fechaHasta
    ) {
        return servicio.obtenerServiciosPendientesPago(fechaDesde, fechaHasta);
    }

    @PutMapping("/servicios-pendientes-pago/actualizar-pago/{idServicio}/{numeroPago}")
    public ResponseEntity<?> actualizarPago(
        @PathVariable Long idServicio,
        @PathVariable int numeroPago,
        @RequestParam boolean pagado
    ) {
        try {
            servicio.actualizarPagoParcial(idServicio, numeroPago, pagado);
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Pago actualizado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }
}
