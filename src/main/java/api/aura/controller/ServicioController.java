package api.aura.controller;

import api.aura.entity.Servicio;
import api.aura.entity.Cliente;
import api.aura.entity.PuntoSuministro;
import api.aura.service.ServicioService;
import api.aura.service.ServicioConPagosService;
import api.aura.dto.ServicioDTO;
import api.aura.dto.GuardarServicioConPagosRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/servicios")
public class ServicioController {

    private static final Logger logger = LoggerFactory.getLogger(ServicioController.class);

    @Autowired
    private ServicioService servicioService;

    @Autowired
    private ServicioConPagosService servicioConPagosService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping(consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<?> crearOActualizarServicio(
            @RequestParam(value = "servicio", required = false) String servicioJson,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            @RequestBody(required = false) JsonNode servicioBody) {

        try {
            Servicio servicio = null;

            // Si viene como JSON en el body, úsalo directamente
            if (servicioBody != null) {
                servicio = parseServicioFromJson(servicioBody);
                logger.info("Servicio recibido directamente del body JSON");
            }
            // Si viene como parte de un multipart form
            else if (servicioJson != null && !servicioJson.isEmpty()) {
                JsonNode jsonNode = objectMapper.readTree(servicioJson);
                servicio = parseServicioFromJson(jsonNode);
                logger.info("Servicio deserializado desde el formulario multipart");

                // Aquí se procesaría el archivo PDF si estuviera implementado
                if (archivo != null && !archivo.isEmpty()) {
                    logger.info("Se recibió un archivo adjunto: {}", archivo.getOriginalFilename());
                    // Implementar el guardado del archivo PDF
                }
            }

            if (servicio == null) {
                logger.error("No se pudo obtener los datos del servicio");
                return ResponseEntity.badRequest().body(Map.of("error", "No se pudieron parsear los datos del servicio"));
            }

            Servicio guardado = servicioService.guardarServicio(servicio);
            return ResponseEntity.ok(guardado);
        } catch (DataIntegrityViolationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error al procesar el servicio", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error al procesar el servicio"));
        }
    }

    /**
     * Persiste el servicio y sus pagos como una unica operacion atomica. Se
     * mantiene el resto de endpoints para no romper consumidores existentes.
     */
    @PostMapping(path = "/guardar-con-pagos", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> guardarConPagos(@RequestBody GuardarServicioConPagosRequest request) {
        try {
            if (request == null || request.servicio() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Los datos del servicio son obligatorios"));
            }
            return ResponseEntity.ok(servicioConPagosService.guardar(request.servicio(), request.pagos()));
        } catch (DataIntegrityViolationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error al guardar servicio y pagos", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage() != null ? e.getMessage() : "Error al guardar el servicio y sus pagos"));
        }
    }

    private Servicio parseServicioFromJson(JsonNode jsonNode) throws Exception {
        Servicio servicio = objectMapper.treeToValue(jsonNode, Servicio.class);

        // Manualmente crear objetos Cliente y PuntoSuministro con los IDs recibidos
        if (jsonNode.has("idCliente") && !jsonNode.get("idCliente").isNull() &&
                !jsonNode.get("idCliente").asText().isEmpty()) {
            Integer idCliente = jsonNode.get("idCliente").asInt();
            Cliente cliente = new Cliente();
            cliente.setId(idCliente);
            servicio.setCliente(cliente);
            logger.info("ID Cliente parseado: {}", idCliente);
        }

        if (jsonNode.has("idPuntoSuministro") && !jsonNode.get("idPuntoSuministro").isNull() &&
                !jsonNode.get("idPuntoSuministro").asText().isEmpty()) {
            Integer idPuntoSuministro = jsonNode.get("idPuntoSuministro").asInt();
            PuntoSuministro puntoSuministro = new PuntoSuministro();
            puntoSuministro.setId(idPuntoSuministro);
            servicio.setPuntoSuministro(puntoSuministro);
            logger.info("ID Punto Suministro parseado: {}", idPuntoSuministro);
        }

        return servicio;
    }

    @PutMapping(path = "/{id}", consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<?> actualizarServicio(
            @PathVariable Integer id,
            @RequestParam(value = "servicio", required = false) String servicioJson,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            @RequestBody(required = false) JsonNode servicioBody) {

        try {
            Servicio servicio = null;

            // Si viene como JSON en el body, úsalo directamente
            if (servicioBody != null) {
                servicio = parseServicioFromJson(servicioBody);
                logger.info("Servicio recibido directamente del body JSON para actualizar");
            }
            // Si viene como parte de un multipart form
            else if (servicioJson != null && !servicioJson.isEmpty()) {
                JsonNode jsonNode = objectMapper.readTree(servicioJson);
                servicio = parseServicioFromJson(jsonNode);
                logger.info("Servicio deserializado desde el formulario multipart para actualizar");

                // Aquí se procesaría el archivo PDF si estuviera implementado
                if (archivo != null && !archivo.isEmpty()) {
                    logger.info("Se recibió un archivo adjunto para actualizar: {}", archivo.getOriginalFilename());
                    // Implementar el guardado del archivo PDF
                }
            }

            if (servicio == null) {
                logger.error("No se pudo obtener los datos del servicio para actualizar");
                return ResponseEntity.badRequest().body(Map.of("error", "No se pudieron parsear los datos del servicio"));
            }

            servicio.setId(id);
            Servicio actualizado = servicioService.guardarServicio(servicio);
            return ResponseEntity.ok(actualizado);
        } catch (DataIntegrityViolationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error al procesar la actualización del servicio", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error al actualizar el servicio"));
        }
    }

    // Renovar un servicio (clona y ajusta fechas)
    @PostMapping("/{id}/renovar")
    public ResponseEntity<Servicio> renovarServicio(@PathVariable Integer id) {
        Servicio renovado = servicioService.renovarServicio(id);
        if (renovado != null)
            return ResponseEntity.ok(renovado);
        return ResponseEntity.notFound().build();
    }

    // Descarga de PDF de un servicio: no implementado todavía y sin consumidor
    // en el frontend actual. Se devuelve 501 (no 404) para no confundirlo con
    // "servicio inexistente" si en el futuro se invoca desde algún cliente.
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargarPDF(@PathVariable Integer id) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_IMPLEMENTED).build();
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

    /**
     * Devuelve los IDs de puntos de suministro que tienen al menos un servicio
     * activo (sin fecha de baja, sin fecha de fin o con fecha de fin futura)
     * para los productos indicados. Evita cargar todos los servicios al frontend.
     */
    @GetMapping("/puntos-con-servicio-activo")
    public ResponseEntity<java.util.Set<Integer>> obtenerPuntosConServicioActivo(
            @RequestParam List<Integer> idProductos) {
        if (idProductos == null || idProductos.isEmpty()) {
            return ResponseEntity.ok(java.util.Collections.emptySet());
        }
        return ResponseEntity.ok(
            servicioService.obtenerIdsPuntosConServicioActivo(idProductos)
        );
    }

    /**
     * Total real de servicios activos hoy (fechaInicioServicio <= hoy && fechaFinServicio > hoy),
     * calculado en base de datos con un COUNT en lugar de estimarlo en el frontend.
     */
    @GetMapping("/estadisticas/activos")
    public ResponseEntity<Map<String, Long>> obtenerTotalServiciosActivos() {
        return ResponseEntity.ok(Map.of("total", servicioService.contarServiciosActivos()));
    }
}
