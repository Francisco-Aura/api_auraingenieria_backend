package api.aura.controller;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import api.aura.entity.Cliente;
import api.aura.entity.Contrato;
import api.aura.entity.PuntoSuministro;
import api.aura.service.ContratoService;

@RestController
@RequestMapping("/api/contratos")
public class ContratoController {

    private static final Logger logger = LoggerFactory.getLogger(ContratoController.class);

    private final ContratoService contratoService;
    private final ObjectMapper objectMapper;

    public ContratoController(ContratoService contratoService, ObjectMapper objectMapper) {
        this.contratoService = contratoService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<?> crearOActualizarContrato(
            @RequestParam(value = "contrato", required = false) String contratoJson,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            @RequestBody(required = false) JsonNode contratoBody) {
        try {
            Contrato contrato = obtenerContrato(contratoJson, contratoBody);
            Contrato guardado = archivo != null && !archivo.isEmpty()
                    ? contratoService.guardarContratoConPdf(contrato, archivo)
                    : contratoService.guardarContrato(contrato);
            return ResponseEntity.ok(guardado);
        } catch (DataIntegrityViolationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error al procesar el contrato", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error al procesar el contrato"));
        }
    }

    @PutMapping(path = "/{id}", consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<?> actualizarContrato(
            @PathVariable Integer id,
            @RequestParam(value = "contrato", required = false) String contratoJson,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            @RequestBody(required = false) JsonNode contratoBody) {
        try {
            Contrato contrato = obtenerContrato(contratoJson, contratoBody);
            contrato.setId(id);
            Contrato actualizado = archivo != null && !archivo.isEmpty()
                    ? contratoService.guardarContratoConPdf(contrato, archivo)
                    : contratoService.guardarContrato(contrato);
            return ResponseEntity.ok(actualizado);
        } catch (DataIntegrityViolationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error al procesar la actualización del contrato", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error al actualizar el contrato"));
        }
    }

    @GetMapping
    public ResponseEntity<List<Contrato>> obtenerTodos() {
        return ResponseEntity.ok(contratoService.obtenerTodos());
    }

    @GetMapping("/activos")
    public ResponseEntity<List<Contrato>> obtenerContratosActivos() {
        return ResponseEntity.ok(contratoService.obtenerContratosActivos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Contrato> obtenerPorId(@PathVariable Integer id) {
        return contratoService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Resource> descargarPdf(@PathVariable Integer id) {
        try {
            Contrato contrato = contratoService.obtenerPorId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Contrato no encontrado"));
            Path ruta = contratoService.obtenerRutaPdf(id);
            String nombre = contrato.getNombreArchivo() == null ? "contrato.pdf" : contrato.getNombreArchivo();
            String seguro = nombre.replace("\"", "").replaceAll("[\r\n]", "");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + seguro + "\"")
                    .body(new PathResource(ruta));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Contrato>> obtenerPorClienteId(@PathVariable Integer clienteId) {
        return ResponseEntity.ok(contratoService.obtenerPorClienteId(clienteId));
    }

    @GetMapping("/punto-suministro/{puntoSuministroId}")
    public ResponseEntity<List<Contrato>> obtenerPorPuntoSuministroId(@PathVariable Integer puntoSuministroId) {
        return ResponseEntity.ok(contratoService.obtenerPorPuntoSuministroId(puntoSuministroId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPorId(@PathVariable Integer id) {
        contratoService.eliminarPorId(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/renovar/{idContratoOriginal}", consumes = { MediaType.APPLICATION_JSON_VALUE,
            MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<?> renovarContrato(
            @PathVariable Integer idContratoOriginal,
            @RequestParam(value = "contrato", required = false) String contratoJson,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            @RequestBody(required = false) JsonNode contratoBody) {
        try {
            Contrato nuevoContrato = obtenerContrato(contratoJson, contratoBody);
            Contrato guardado = archivo != null && !archivo.isEmpty()
                    ? contratoService.renovarContratoConPdf(idContratoOriginal, nuevoContrato, archivo)
                    : contratoService.renovarContrato(idContratoOriginal, nuevoContrato);
            return ResponseEntity.ok(guardado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al renovar contrato {}", idContratoOriginal, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error al renovar el contrato"));
        }
    }

    @PatchMapping("/{id}/comentario-renovacion")
    public ResponseEntity<?> actualizarComentarioRenovacion(
            @PathVariable Integer id,
            @RequestBody Map<String, String> body) {
        try {
            String comentario = body.getOrDefault("comentarioRenovacion", "");
            Contrato actualizado = contratoService.actualizarComentarioRenovacion(id, comentario);
            return ResponseEntity.ok(Map.of("success", true, "comentarioRenovacion",
                    actualizado.getComentarioRenovacion() != null ? actualizado.getComentarioRenovacion() : ""));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al actualizar comentario de renovación en contrato {}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error al actualizar comentario"));
        }
    }

    private Contrato obtenerContrato(String contratoJson, JsonNode contratoBody) throws Exception {
        if (contratoBody != null) {
            return parseContratoFromJson(contratoBody);
        }
        if (contratoJson != null && !contratoJson.isBlank()) {
            return parseContratoFromJson(objectMapper.readTree(contratoJson));
        }
        throw new IllegalArgumentException("No se pudieron parsear los datos del contrato");
    }

    private Contrato parseContratoFromJson(JsonNode jsonNode) throws Exception {
        Contrato contrato = objectMapper.treeToValue(jsonNode, Contrato.class);

        if (jsonNode.has("idCliente") && !jsonNode.get("idCliente").isNull()
                && !jsonNode.get("idCliente").asText().isEmpty()) {
            Cliente cliente = new Cliente();
            cliente.setId(jsonNode.get("idCliente").asInt());
            contrato.setCliente(cliente);
        }

        if (jsonNode.has("idPuntoSuministro") && !jsonNode.get("idPuntoSuministro").isNull()
                && !jsonNode.get("idPuntoSuministro").asText().isEmpty()) {
            PuntoSuministro puntoSuministro = new PuntoSuministro();
            puntoSuministro.setId(jsonNode.get("idPuntoSuministro").asInt());
            contrato.setPuntoSuministro(puntoSuministro);
        }

        return contrato;
    }
}
