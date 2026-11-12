package api.aura.controller.mcas;

import api.aura.config.AuraUserHolder;
import api.aura.config.McasAccesoUsuario;
import api.aura.dto.mcas.McasVistaPreviaDTO;
import api.aura.service.mcas.McasVistaPreviaService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Comisión estimada desde el formulario del contrato. Abierta a cualquier usuario
 * identificado (quien graba el contrato); nunca guarda nada. El importe solo lo
 * reciben los administradores: el resto ve si faltan datos y cuáles.
 */
@RestController
@RequestMapping("/api/mcas/vista-previa")
public class McasVistaPreviaController {

    private final McasVistaPreviaService vistaPrevia;

    public McasVistaPreviaController(McasVistaPreviaService vistaPrevia) {
        this.vistaPrevia = vistaPrevia;
    }

    /** Cuerpo: el mismo JSON de contrato que {@code POST /api/contratos} (puede traer el id del contrato). */
    @McasAccesoUsuario
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> calcular(@RequestBody JsonNode contrato) {
        try {
            McasVistaPreviaDTO resultado = vistaPrevia.calcular(contrato);
            return ResponseEntity.ok(AuraUserHolder.esAdmin() ? resultado : McasVistaPreviaService.sinImportes(resultado));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
