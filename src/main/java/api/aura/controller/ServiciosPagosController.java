package api.aura.controller;

import api.aura.dto.CuotaServicioDTO;
import api.aura.entity.ServiciosPagos;
import api.aura.service.ServiciosPagosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/servicios-pagos")
public class ServiciosPagosController {

    private static final Logger logger = LoggerFactory.getLogger(ServiciosPagosController.class);

    @Autowired
    private ServiciosPagosService serviciosPagosService;

    // Pagos de los servicios: solo administradores, como su gestión y el informe de pagos
    // (la pantalla solo se los muestra a ellos)
    @GetMapping("/servicio/{idServicio}")
    public ResponseEntity<ServiciosPagos> obtenerPagosPorServicio(@PathVariable Integer idServicio) {
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(serviciosPagosService.obtenerPagosPorServicio(idServicio));
    }

    /**
     * Los planes de pagos de varios servicios en una sola petición (la ventana de servicios
     * de un punto pedía uno por servicio). Mismo contenido que /servicio/{id}.
     */
    @GetMapping("/servicios")
    public ResponseEntity<List<ServiciosPagos>> obtenerPagosPorServicios(@RequestParam("ids") List<Integer> ids) {
        SesionActual.exigirAdmin();
        if (ids.size() > MAXIMO_SERVICIOS_POR_PETICION) {
            throw new IllegalArgumentException("Demasiados servicios en una sola petición");
        }
        return ResponseEntity.ok(serviciosPagosService.obtenerPagosPorServicios(ids));
    }

    private static final int MAXIMO_SERVICIOS_POR_PETICION = 500;

    @GetMapping("/servicio/{idServicio}/cuotas")
    public ResponseEntity<List<CuotaServicioDTO>> obtenerCuotasPorServicio(@PathVariable Integer idServicio) {
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(serviciosPagosService.obtenerCuotasPorServicio(idServicio));
    }

    @PutMapping("/servicio/{idServicio}")
    public ResponseEntity<ServiciosPagos> actualizarPagosServicio(@PathVariable Integer idServicio,
            @RequestBody ServiciosPagos pagos) {
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(serviciosPagosService.actualizarPagosServicio(idServicio, pagos));
    }

    @PutMapping("/cuota/{idCuota}")
    public ResponseEntity<CuotaServicioDTO> actualizarCuota(@PathVariable Integer idCuota,
            @RequestBody CuotaServicioDTO cuota) {
        SesionActual.exigirAdmin();
        return ResponseEntity.ok(serviciosPagosService.actualizarCuota(idCuota, cuota));
    }

    @PostMapping
    public ResponseEntity<ServiciosPagos> crearServicioPagos(@RequestBody ServiciosPagos servicioPagos) {
        SesionActual.exigirAdmin();
        try {
            // Asegurarse de que el servicio y otros objetos relacionados estén
            // correctamente vinculados
            if (servicioPagos.getServicio() == null || servicioPagos.getServicio().getId() == null) {
                return ResponseEntity.badRequest().body(null);
            }

            // Guardar el objeto
            ServiciosPagos guardado = serviciosPagosService.guardar(servicioPagos);
            return ResponseEntity.ok(guardado);
        } catch (Exception e) {
            logger.error("Error al guardar los pagos del servicio", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @PostMapping("/generar-plan")
    public ResponseEntity<?> generarPlanPagos(@RequestBody Map<String, Object> params) {
        SesionActual.exigirAdmin();
        try {
            Integer idPagoPlazos = (Integer) params.get("idPagoPlazos");
            Double importeTotal = Double.valueOf(params.get("importeTotal").toString());

            ServiciosPagos plantilla = new ServiciosPagos();

            // Asegurarse de que los campos importe, descuento e importeFinal se establezcan en la plantilla
            if (params.containsKey("importe")) {
                plantilla.setImporte(Double.valueOf(params.get("importe").toString()));
            }
            if (params.containsKey("descuento")) {
                plantilla.setDescuento(Double.valueOf(params.get("descuento").toString()));
            }
            if (params.containsKey("importeFinal")) {
                plantilla.setImporteFinal(Double.valueOf(params.get("importeFinal").toString()));
            } else { // Si importeFinal no se envía explícitamente, usar importeTotal (calculado en frontend)
                plantilla.setImporteFinal(importeTotal);
            }

            if (params.containsKey("fechaInicio")) {
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                // Sin esto, "2026-02-30" no falla: se convierte en el 2 de marzo y todo
                // el plan de cuotas sale con las fechas corridas sin avisar.
                formatter.setLenient(false);
                Date fechaInicio = formatter.parse(params.get("fechaInicio").toString());
                plantilla.setPrimerPagoFecha(fechaInicio);
            }

            ServiciosPagos resultado = serviciosPagosService.generarCuotasSegunModalidad(
                    plantilla, idPagoPlazos, plantilla.getImporteFinal()); // Pasar importeFinal para el cálculo

            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            logger.error("Error al generar el plan de pagos", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error al generar el plan de pagos"));
        }
    }

    @DeleteMapping("/servicio/{idServicio}")
    public ResponseEntity<Void> eliminarPagosPorServicio(@PathVariable Integer idServicio) {
        SesionActual.exigirAdmin();
        serviciosPagosService.eliminarPagosPorServicio(idServicio);
        return ResponseEntity.noContent().build();
    }
}