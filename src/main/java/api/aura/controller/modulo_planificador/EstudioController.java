package api.aura.controller.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.service.modulo_planificador.EstudioService;
import api.aura.service.modulo_planificador.PropuestaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Estudios fotovoltaicos del Planificador: registro, SIPS y curva de consumo, cálculo e informe. */
@RestController
@RequestMapping("/api/planificador/studies")
@RequiredArgsConstructor
public class EstudioController {

    private final EstudioService estudios;

    @GetMapping
    public List<Map<String, Object>> listar() {
        return estudios.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crear(@RequestBody Map<String, Object> cuerpo) {
        return estudios.crear(cuerpo);
    }

    @GetMapping("/{id}")
    public Map<String, Object> obtener(@PathVariable String id) {
        return estudios.obtener(id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> actualizar(@PathVariable String id, @RequestBody Map<String, Object> cuerpo) {
        return estudios.actualizar(id, cuerpo);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String id) {
        estudios.eliminar(id);
    }

    @PostMapping(value = "/{id}/sips", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> sips(@PathVariable String id, @RequestParam("file") MultipartFile fichero) {
        return estudios.subirSips(id, bytes(fichero));
    }

    @PostMapping(value = "/{id}/curve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> curva(@PathVariable String id, @RequestParam("file") MultipartFile fichero) {
        return estudios.subirCurva(id, bytes(fichero), fichero.getOriginalFilename());
    }

    @GetMapping("/{id}/report.html")
    public ResponseEntity<byte[]> html(@PathVariable String id) {
        return descarga(estudios.html(id), MediaType.parseMediaType("text/html; charset=utf-8"));
    }

    /** El informe en PDF; 501 si el servidor no tiene Chrome (la pantalla ofrece entonces imprimir el HTML). */
    @GetMapping("/{id}/report.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String id) {
        return descarga(estudios.pdf(id), MediaType.APPLICATION_PDF);
    }

    private static byte[] bytes(MultipartFile f) {
        if (f == null || f.isEmpty()) throw new ErrorApi(HttpStatus.BAD_REQUEST, "El fichero está vacío.");
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw new ErrorApi(HttpStatus.BAD_REQUEST, "No se pudo leer el fichero subido.");
        }
    }

    private static ResponseEntity<byte[]> descarga(PropuestaService.Documento d, MediaType tipo) {
        String nombre = java.net.URLEncoder.encode(d.nombre(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok().contentType(tipo).header("Content-Disposition", "inline; filename*=UTF-8''" + nombre).body(d.contenido());
    }
}
