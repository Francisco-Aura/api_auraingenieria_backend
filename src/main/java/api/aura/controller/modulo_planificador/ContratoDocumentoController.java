package api.aura.controller.modulo_planificador;

import api.aura.service.modulo_planificador.ContratoDocumentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** El contrato de un proyecto, solo en PDF. */
@RestController
@RequestMapping("/api/planificador")
@RequiredArgsConstructor
public class ContratoDocumentoController {

    private final ContratoDocumentoService contratoDocumento;

    @PostMapping("/contract/generate")
    public ResponseEntity<byte[]> generarSinGuardar(@RequestBody Map<String, Object> datos, @RequestParam(defaultValue = "pdf") String format) {
        return enPdf(contratoDocumento.generarSinGuardar(datos, format));
    }

    @PostMapping("/projects/{id}/contract-document")
    public Map<String, Object> generar(@PathVariable String id, @RequestBody(required = false) Map<String, Object> cuerpo) {
        return contratoDocumento.generar(id, cuerpo == null ? Map.of() : cuerpo);
    }

    @GetMapping("/projects/{id}/contract-document")
    public ResponseEntity<byte[]> descargar(@PathVariable String id, @RequestParam(defaultValue = "pdf") String format) {
        return enPdf(contratoDocumento.descargar(id, format));
    }

    private static ResponseEntity<byte[]> enPdf(ContratoDocumentoService.Descarga d) {
        String nombre = URLEncoder.encode(d.nombre(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Content-Disposition", "inline; filename*=UTF-8''" + nombre).body(d.pdf());
    }
}
