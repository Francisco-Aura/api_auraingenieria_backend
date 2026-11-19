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

    // ------------------------------------------------------------------ sin guardar (estudios locales y copias de trabajo)

    /** Calcula un estudio que llega entero, sin guardarlo. */
    @PostMapping("/calculate")
    public Map<String, Object> calcular(@RequestBody Map<String, Object> cuerpo) {
        return estudios.calcularSinGuardar(cuerpo);
    }

    /** Las recomendaciones de la biblioteca para este estudio: con sus números y cuáles se sugieren. */
    @PostMapping("/recommendations")
    public Map<String, Object> recomendaciones(@RequestBody Map<String, Object> cuerpo) {
        return estudios.recomendacionesDe(cuerpo);
    }

    /** Busca un lugar por su nombre y devuelve sus coordenadas (para la ubicación de la instalación). */
    @GetMapping("/places")
    public List<Map<String, Object>> lugares(@RequestParam("q") String texto) {
        return estudios.buscarLugar(texto);
    }

    /** Lee un SIPS y devuelve sus datos, sin guardarlo. */
    @PostMapping(value = "/read-sips", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> leerSips(@RequestParam("file") MultipartFile fichero) {
        return estudios.leerSips(bytes(fichero));
    }

    /** Lee una curva horaria y la devuelve como texto, sin guardarla. */
    @PostMapping(value = "/read-curve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> leerCurva(@RequestParam("file") MultipartFile fichero) {
        return estudios.leerCurva(bytes(fichero), fichero.getOriginalFilename());
    }

    /** El informe de un estudio que llega entero, sin guardarlo. */
    @PostMapping("/report.html")
    public ResponseEntity<byte[]> htmlSinGuardar(@RequestBody Map<String, Object> cuerpo) {
        return descarga(estudios.informeSinGuardar(cuerpo, false), MediaType.parseMediaType("text/html; charset=utf-8"));
    }

    @PostMapping("/report.pdf")
    public ResponseEntity<byte[]> pdfSinGuardar(@RequestBody Map<String, Object> cuerpo) {
        return descarga(estudios.informeSinGuardar(cuerpo, true), MediaType.APPLICATION_PDF);
    }

    // ------------------------------------------------------------------ estudios compartidos (guardados en el servidor)

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
