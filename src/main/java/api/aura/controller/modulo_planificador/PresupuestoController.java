package api.aura.controller.modulo_planificador;

import api.aura.dto.modulo_planificador.PresupuestoGuardarRequest;
import api.aura.service.modulo_planificador.ClientesIntranetPort;
import api.aura.service.modulo_planificador.OfertaPdf;
import api.aura.service.modulo_planificador.PresupuestoPdf;
import api.aura.service.modulo_planificador.PresupuestoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import api.aura.config.ErrorApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/planificador")
@RequiredArgsConstructor
public class PresupuestoController {

    private final PresupuestoService presupuestoService;
    private final ClientesIntranetPort clientes;
    private final PresupuestoPdf presupuestoPdf;
    private final OfertaPdf ofertaPdf;

    /**
     * Lee el presupuesto de la oferta (PDF; el cuerpo es el archivo) y devuelve los datos que se pueden leer. No guarda nada: el formulario los
     * usa para rellenar el contrato inicial y quien crea el proyecto los revisa antes de aceptarlos.
     */
    @PostMapping("/budget/extract")
    public Map<String, Object> leerOferta(@RequestBody(required = false) byte[] datos, @RequestParam(name = "name", defaultValue = "presupuesto.pdf") String nombre) {
        if (datos == null || datos.length == 0) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "El archivo está vacío");
        }
        if (datos.length > 30 * 1024 * 1024) {
            throw new ErrorApi(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el máximo de 30 MB");
        }
        if (datos.length < 5 || datos[0] != '%' || datos[1] != 'P' || datos[2] != 'D' || datos[3] != 'F' || datos[4] != '-') {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "Solo se puede subir el presupuesto en PDF.");
        }
        try {
            return ofertaPdf.analizar(datos, nombre);
        } catch (OfertaPdf.DocumentoNoSoportado e) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    /** PDF del presupuesto en curso: `interno` (costes y beneficio) o `cliente` (solo el precio de venta). No guarda nada. */
    @PostMapping("/budget/pdf")
    public ResponseEntity<byte[]> pdfDelPresupuesto(@RequestBody Map<String, Object> cuerpo, @RequestParam(name = "mode", defaultValue = "interno") String modo) {
        if (!modo.equals("interno") && !modo.equals("cliente")) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "El PDF puede ser «interno» o «cliente».");
        }
        java.time.LocalDate hoy = java.time.LocalDate.now();
        byte[] pdf = presupuestoPdf.generar(cuerpo, modo, hoy);
        String nombre = java.net.URLEncoder.encode(PresupuestoPdf.nombreBase(cuerpo.get("client") == null ? "" : String.valueOf(cuerpo.get("client")), modo, hoy), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Content-Disposition", "inline; filename*=UTF-8''" + nombre + ".pdf").body(pdf);
    }

    /** Clientes de la intranet que casan con el texto; `enabled` es false si no hay conexión (solo potenciales). */
    @GetMapping("/clients")
    public Map<String, Object> clientes(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> r = new LinkedHashMap<>();
        boolean habilitado = clientes.habilitado();
        r.put("enabled", habilitado);
        r.put("items", habilitado ? clientes.buscar(q, size) : List.of());
        return r;
    }

    @GetMapping("/budgets")
    public List<Map<String, Object>> listar() {
        return presupuestoService.listar();
    }

    @GetMapping("/budgets/pending-validation")
    public Map<String, Object> pendientesDeValidar() {
        return presupuestoService.pendientesDeValidar();
    }

    @GetMapping("/budgets/{id}")
    public Map<String, Object> obtener(@PathVariable String id) {
        return presupuestoService.obtener(id);
    }

    @PostMapping("/budgets")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crear(@RequestBody @Valid PresupuestoGuardarRequest cuerpo) {
        return presupuestoService.crear(cuerpo);
    }

    @PutMapping("/budgets/{id}")
    public Map<String, Object> actualizar(@PathVariable String id, @RequestBody @Valid PresupuestoGuardarRequest cuerpo) {
        return presupuestoService.actualizar(id, cuerpo);
    }

    @PostMapping("/budgets/{id}/send")
    public Map<String, Object> enviar(@PathVariable String id) {
        return presupuestoService.enviar(id);
    }

    @PostMapping("/budgets/{id}/variant-name")
    public Map<String, Object> renombrarVariante(@PathVariable String id, @RequestBody(required = false) Map<String, Object> cuerpo) {
        return presupuestoService.renombrarVariante(id, texto(cuerpo, "name"));
    }

    @PostMapping("/budgets/{id}/variant")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> duplicar(@PathVariable String id, @RequestBody(required = false) Map<String, Object> cuerpo) {
        return presupuestoService.duplicar(id, texto(cuerpo, "name"));
    }

    @PostMapping("/budgets/{id}/validate")
    public Map<String, Object> validar(@PathVariable String id, @RequestBody(required = false) Map<String, Object> cuerpo) {
        return presupuestoService.validar(id, texto(cuerpo, "cif"));
    }

    @PostMapping("/budgets/{id}/unvalidate")
    public Map<String, Object> desvalidar(@PathVariable String id) {
        return presupuestoService.desvalidar(id);
    }

    @DeleteMapping("/budgets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String id) {
        presupuestoService.eliminar(id);
    }

    private static String texto(Map<String, Object> cuerpo, String clave) {
        Object v = cuerpo == null ? null : cuerpo.get(clave);
        return v == null ? "" : String.valueOf(v);
    }
}
