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

import java.util.List;

@RestController
@RequestMapping("/api/contratos")
public class ContratoController {

    private static final Logger logger = LoggerFactory.getLogger(ContratoController.class);

    @Autowired
    private ContratoService contratoService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping(consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<Contrato> crearOActualizarContrato(
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
                    // TODO: Implementar el guardado del archivo PDF
                }
            }

            if (contrato == null) {
                logger.error("No se pudo obtener los datos del contrato");
                return ResponseEntity.badRequest().build();
            }

            Contrato guardado = contratoService.guardarContrato(contrato);
            return ResponseEntity.ok(guardado);
        } catch (Exception e) {
            logger.error("Error al procesar el contrato", e);
            return ResponseEntity.badRequest().build();
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
    public ResponseEntity<Contrato> actualizarContrato(
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
                    // TODO: Implementar el guardado del archivo PDF
                }
            }

            if (contrato == null) {
                logger.error("No se pudo obtener los datos del contrato para actualizar");
                return ResponseEntity.badRequest().build();
            }

            contrato.setId(id);
            Contrato actualizado = contratoService.guardarContrato(contrato);
            return ResponseEntity.ok(actualizado);
        } catch (Exception e) {
            logger.error("Error al procesar la actualización del contrato", e);
            return ResponseEntity.badRequest().build();
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
}
