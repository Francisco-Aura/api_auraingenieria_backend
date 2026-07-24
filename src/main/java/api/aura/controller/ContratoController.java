package api.aura.controller;

import api.aura.entity.Contrato;
import api.aura.entity.Cliente;
import api.aura.entity.PuntoSuministro;
import api.aura.service.ContratoService;
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
@RequestMapping("/api/contratos")
public class ContratoController {

    private static final Logger logger = LoggerFactory.getLogger(ContratoController.class);

    @Autowired
    private ContratoService contratoService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping(consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<?> crearOActualizarContrato(
            @RequestParam(value = "contrato", required = false) String contratoJson,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            @RequestBody(required = false) JsonNode contratoBody) {

        try {
            Contrato contrato = null;

            // Si viene como JSON en el body, úsalo directamente
            if (contratoBody != null) {
                contrato = parseContratoFromJson(contratoBody);
                logger.info("Contrato recibido directamente del body JSON");
            }
            // Si viene como parte de un multipart form
            else if (contratoJson != null && !contratoJson.isEmpty()) {
                JsonNode jsonNode = objectMapper.readTree(contratoJson);
                contrato = parseContratoFromJson(jsonNode);
                logger.info("Contrato deserializado desde el formulario multipart");

                // Aquí se procesaría el archivo PDF si estuviera implementado
                if (archivo != null && !archivo.isEmpty()) {
                    logger.info("Se recibió un archivo adjunto: {}", archivo.getOriginalFilename());
                    // Implementar el guardado del archivo PDF
                }
            }

            if (contrato == null) {
                logger.error("No se pudo obtener los datos del contrato");
                return ResponseEntity.badRequest().body(Map.of("error", "No se pudieron parsear los datos del contrato"));
            }

            Contrato guardado = contratoService.guardarContrato(contrato);
            return ResponseEntity.ok(guardado);
        } catch (DataIntegrityViolationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error al procesar el contrato", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error al procesar el contrato"));
        }
    }

    private Contrato parseContratoFromJson(JsonNode jsonNode) throws Exception {
        Contrato contrato = objectMapper.treeToValue(jsonNode, Contrato.class);

        // Manualmente crear objetos Cliente y PuntoSuministro con los IDs recibidos
        if (jsonNode.has("idCliente") && !jsonNode.get("idCliente").isNull() &&
                !jsonNode.get("idCliente").asText().isEmpty()) {
            Integer idCliente = jsonNode.get("idCliente").asInt();
            Cliente cliente = new Cliente();
            cliente.setId(idCliente);
            contrato.setCliente(cliente);
            logger.info("ID Cliente parseado: {}", idCliente);
        }

        if (jsonNode.has("idPuntoSuministro") && !jsonNode.get("idPuntoSuministro").isNull() &&
                !jsonNode.get("idPuntoSuministro").asText().isEmpty()) {
            Integer idPuntoSuministro = jsonNode.get("idPuntoSuministro").asInt();
            PuntoSuministro puntoSuministro = new PuntoSuministro();
            puntoSuministro.setId(idPuntoSuministro);
            contrato.setPuntoSuministro(puntoSuministro);
            logger.info("ID Punto Suministro parseado: {}", idPuntoSuministro);
        }

        return contrato;
    }

    @PutMapping(path = "/{id}", consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<?> actualizarContrato(
            @PathVariable Integer id,
            @RequestParam(value = "contrato", required = false) String contratoJson,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            @RequestBody(required = false) JsonNode contratoBody) {

        try {
            Contrato contrato = null;

            // Si viene como JSON en el body, úsalo directamente
            if (contratoBody != null) {
                contrato = parseContratoFromJson(contratoBody);
                logger.info("Contrato recibido directamente del body JSON para actualizar");
            }
            // Si viene como parte de un multipart form
            else if (contratoJson != null && !contratoJson.isEmpty()) {
                JsonNode jsonNode = objectMapper.readTree(contratoJson);
                contrato = parseContratoFromJson(jsonNode);
                logger.info("Contrato deserializado desde el formulario multipart para actualizar");

                // Aquí se procesaría el archivo PDF si estuviera implementado
                if (archivo != null && !archivo.isEmpty()) {
                    logger.info("Se recibió un archivo adjunto para actualizar: {}", archivo.getOriginalFilename());
                    // Implementar el guardado del archivo PDF
                }
            }

            if (contrato == null) {
                logger.error("No se pudo obtener los datos del contrato para actualizar");
                return ResponseEntity.badRequest().body(Map.of("error", "No se pudieron parsear los datos del contrato"));
            }

            contrato.setId(id);
            Contrato actualizado = contratoService.guardarContrato(contrato);
            return ResponseEntity.ok(actualizado);
        } catch (DataIntegrityViolationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error al procesar la actualización del contrato", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error al actualizar el contrato"));
        }
    }

    @GetMapping
    public ResponseEntity<List<Contrato>> obtenerTodos() {
        List<Contrato> contratos = contratoService.obtenerTodos();
        return ResponseEntity.ok(contratos);
    }

    @GetMapping("/activos")
    public ResponseEntity<List<Contrato>> obtenerContratosActivos() {
        List<Contrato> contratos = contratoService.obtenerContratosActivos();
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

    @PostMapping("/renovar/{idContratoOriginal}")
    public ResponseEntity<?> renovarContrato(
            @PathVariable Integer idContratoOriginal,
            @RequestBody JsonNode contratoBody) {
        try {
            Contrato nuevoContrato = parseContratoFromJson(contratoBody);
            Contrato guardado = contratoService.renovarContrato(idContratoOriginal, nuevoContrato);
            return ResponseEntity.ok(guardado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al renovar contrato {}", idContratoOriginal, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error al renovar el contrato"));
        }
    }

    @PatchMapping("/{id}/comentario-renovacion")
    public ResponseEntity<?> actualizarComentarioRenovacion(
            @PathVariable Integer id,
            @RequestBody Map<String, String> body) {
        try {
            String comentario = body.getOrDefault("comentarioRenovacion", "");
            Contrato actualizado = contratoService.actualizarComentarioRenovacion(id, comentario);
            return ResponseEntity.ok(Map.of("success", true, "comentarioRenovacion", actualizado.getComentarioRenovacion() != null ? actualizado.getComentarioRenovacion() : ""));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al actualizar comentario de renovación en contrato {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error al actualizar comentario"));
        }
    }
}
