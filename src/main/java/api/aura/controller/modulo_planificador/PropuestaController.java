package api.aura.controller.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.dto.modulo_planificador.PresupuestoGuardarRequest;
import jakarta.validation.Valid;
import api.aura.service.modulo_planificador.PropuestaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/planificador")
@RequiredArgsConstructor
public class PropuestaController {


    private final PropuestaService propuestaService;

    @GetMapping("/budgets/{presupuestoId}/proposals")
    public List<Map<String, Object>> listar(@PathVariable String presupuestoId) {
        return propuestaService.listar(presupuestoId);
    }

    @PostMapping("/budgets/{presupuestoId}/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crear(@PathVariable String presupuestoId) {
        return propuestaService.crear(presupuestoId);
    }

    @PostMapping("/proposals/{id}/send")
    public Map<String, Object> enviar(@PathVariable String id) {
        return propuestaService.enviar(id);
    }

    @PostMapping("/proposals/{id}/approve")
    public Map<String, Object> aprobar(@PathVariable String id, @RequestBody(required = false) Map<String, Object> cuerpo) {
        return propuestaService.aprobar(id, nota(cuerpo));
    }

    @PostMapping("/proposals/{id}/reject")
    public Map<String, Object> rechazar(@PathVariable String id, @RequestBody(required = false) Map<String, Object> cuerpo) {
        return propuestaService.rechazar(id, nota(cuerpo));
    }

    @PutMapping("/proposals/{id}/logo")
    public Map<String, Object> guardarLogo(@PathVariable String id, @RequestBody Map<String, Object> cuerpo) {
        Object logo = cuerpo.get("logo");
        return propuestaService.guardarLogo(id, logo == null ? null : String.valueOf(logo));
    }

    @DeleteMapping("/proposals/{id}/logo")
    public Map<String, Object> borrarLogo(@PathVariable String id) {
        return propuestaService.borrarLogo(id);
    }

    @DeleteMapping("/proposals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String id) {
        propuestaService.eliminar(id);
    }

    // PowerPoint (editable) y presentación HTML (imprimible a PDF) de la propuesta

    @GetMapping("/proposals/{id}/pptx")
    public ResponseEntity<byte[]> pptx(@PathVariable String id) {
        return descarga(propuestaService.pptx(id), MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation"));
    }

    @GetMapping("/proposals/{id}/html")
    public ResponseEntity<byte[]> html(@PathVariable String id) {
        return descarga(propuestaService.html(id), MediaType.parseMediaType("text/html; charset=utf-8"));
    }

    /** La propuesta guardada en PDF, con la página de firma; 501 si el servidor no tiene Chrome (el cliente ofrece entonces imprimir el HTML). */
    @GetMapping("/proposals/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String id) {
        return descarga(propuestaService.pdf(id), MediaType.APPLICATION_PDF);
    }

    /** La presentación para el cliente del presupuesto en curso (mismo cuerpo que al guardarlo). No guarda nada. */
    @PostMapping("/budget/proposal-html")
    public ResponseEntity<byte[]> htmlDelBorrador(@Valid @RequestBody PresupuestoGuardarRequest cuerpo) {
        return descarga(propuestaService.htmlDeBorrador(cuerpo), MediaType.parseMediaType("text/html; charset=utf-8"));
    }

    /** La misma propuesta en PDF; 501 si el servidor no tiene Chrome (el cliente ofrece entonces imprimir el HTML). */
    @PostMapping("/budget/proposal-pdf")
    public ResponseEntity<byte[]> pdfDelBorrador(@Valid @RequestBody PresupuestoGuardarRequest cuerpo) {
        return descarga(propuestaService.pdfDeBorrador(cuerpo), MediaType.APPLICATION_PDF);
    }

    private static ResponseEntity<byte[]> descarga(PropuestaService.Documento d, MediaType tipo) {
        String nombre = java.net.URLEncoder.encode(d.nombre(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok().contentType(tipo).header("Content-Disposition", "attachment; filename*=UTF-8''" + nombre).body(d.contenido());
    }

    private static String nota(Map<String, Object> cuerpo) {
        Object v = cuerpo == null ? null : cuerpo.get("note");
        return v == null ? "" : String.valueOf(v);
    }
}
