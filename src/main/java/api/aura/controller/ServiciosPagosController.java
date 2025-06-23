package api.aura.controller;

import api.aura.dto.CuotaServicioDTO;
import api.aura.entity.ServiciosPagos;
import api.aura.service.ServiciosPagosService;
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

    @Autowired
    private ServiciosPagosService serviciosPagosService;

    @GetMapping("/servicio/{idServicio}")
    public ResponseEntity<ServiciosPagos> obtenerPagosPorServicio(@PathVariable Integer idServicio) {
        return ResponseEntity.ok(serviciosPagosService.obtenerPagosPorServicio(idServicio));
    }

    @GetMapping("/servicio/{idServicio}/cuotas")
    public ResponseEntity<List<CuotaServicioDTO>> obtenerCuotasPorServicio(@PathVariable Integer idServicio) {
        return ResponseEntity.ok(serviciosPagosService.obtenerCuotasPorServicio(idServicio));
    }

    @PutMapping("/servicio/{idServicio}")
    public ResponseEntity<ServiciosPagos> actualizarPagosServicio(@PathVariable Integer idServicio,
            @RequestBody ServiciosPagos pagos) {
        return ResponseEntity.ok(serviciosPagosService.actualizarPagosServicio(idServicio, pagos));
    }

    @PutMapping("/cuota/{idCuota}")
    public ResponseEntity<CuotaServicioDTO> actualizarCuota(@PathVariable Integer idCuota,
            @RequestBody CuotaServicioDTO cuota) {
        return ResponseEntity.ok(serviciosPagosService.actualizarCuota(idCuota, cuota));
    }

    @PostMapping
    public ResponseEntity<ServiciosPagos> crearServicioPagos(@RequestBody ServiciosPagos servicioPagos) {
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
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @PostMapping("/generar-plan")
    public ResponseEntity<ServiciosPagos> generarPlanPagos(@RequestBody Map<String, Object> params) {
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
                Date fechaInicio = formatter.parse(params.get("fechaInicio").toString());
                plantilla.setPrimerPagoFecha(fechaInicio);
            }

            ServiciosPagos resultado = serviciosPagosService.generarCuotasSegunModalidad(
                    plantilla, idPagoPlazos, plantilla.getImporteFinal()); // Pasar importeFinal para el cálculo

            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            System.err.println("Error en generarPlanPagos del controller: " + e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}