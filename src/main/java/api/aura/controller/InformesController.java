package api.aura.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import api.aura.dto.informes.*;
import api.aura.dto.ServicioPendientePagoDTO;
import api.aura.service.InformesService;


@RestController
@RequestMapping("/api/informes")
public class InformesController {
    private static final Logger logger = LoggerFactory.getLogger(InformesController.class);

    private final InformesService servicio;

    public InformesController(InformesService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/contratos-pendientes-renovacion")
    public List<ContratoPendienteRenovacionDTO> obtenerContratosPendientesRenovacion(
            @RequestParam(required = false) Integer nivelAcceso,
            @RequestHeader(value = "X-Aura-Usuario-Nombre", required = false) String usuarioNombre) {
        return servicio.obtenerContratosPendientesRenovacion(nivelAcceso, usuarioNombre);
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
            logger.error("Error al actualizar el contrato pendiente de renovación con id {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "No se pudo actualizar el contrato pendiente de renovación"));
        }
    }

    @PutMapping("/puntos-suministro-sin-contrato/actualizar/{id}")
    public ResponseEntity<?> actualizarPuntoSuministroSinContrato(@PathVariable String id, @RequestBody PuntosSuministroSinContratoDTO dto) {
        try {
            PuntosSuministroSinContratoDTO actualizado = servicio.actualizarPuntoSuministroSinContrato(id, dto);
            return ResponseEntity.ok(Map.of("success", true, "data", actualizado));
        } catch (Exception e) {
            logger.error("Error al actualizar el punto de suministro sin contrato con id {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "No se pudo actualizar el punto de suministro sin contrato"));
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
            logger.error("Error al actualizar el pago {} del servicio {}", numeroPago, idServicio, e);
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", "No se pudo actualizar el pago"));
        }
    }

    @PutMapping("/servicios-pendientes-pago/marcar-siguiente-pago/{idServicio}")
    public ResponseEntity<?> marcarSiguientePagoPendiente(@PathVariable Long idServicio) {
        try {
            servicio.marcarSiguientePagoPendiente(idServicio);
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Siguiente pago marcado como pagado correctamente"));
        } catch (Exception e) {
            logger.error("Error al marcar el siguiente pago pendiente del servicio {}", idServicio, e);
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", "No se pudo marcar el siguiente pago como pagado"));
        }
    }

    @GetMapping("/contratos-vigentes")
    public List<ContratoVigenteDTO> obtenerContratosVigentes() {
        return servicio.obtenerContratosVigentes();
    }

    @GetMapping("/servicios-vigentes")
    public List<ServicioVigenteDTO> obtenerServiciosVigentes(
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fechaDesde,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fechaHasta
    ) {
        return servicio.obtenerServiciosVigentes(fechaDesde, fechaHasta);
    }

    @GetMapping("/contactos-clientes")
    public List<ContactoClienteDTO> obtenerContactosClientes() {
        return servicio.obtenerContactosClientes();
    }

    @GetMapping("/contratos-historico")
    public List<ContratoHistoricoDTO> obtenerContratosHistorico() {
        return servicio.obtenerContratosHistorico();
    }
}
