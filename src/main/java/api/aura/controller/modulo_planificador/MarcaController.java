package api.aura.controller.modulo_planificador;

import api.aura.service.modulo_planificador.MarcaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/planificador/brands")
@RequiredArgsConstructor
public class MarcaController {

    private final MarcaService marcaService;

    @GetMapping
    public List<Map<String, Object>> listar() {
        return marcaService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crear(@RequestBody Map<String, Object> cuerpo) {
        Object nombre = cuerpo.get("name");
        return marcaService.crear(nombre == null ? "" : String.valueOf(nombre));
    }

    @DeleteMapping("/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String nombre) {
        marcaService.eliminar(nombre);
    }

    @PutMapping("/{nombre}/logo")
    public Map<String, Object> guardarLogo(@PathVariable String nombre, @RequestBody Map<String, Object> cuerpo) {
        Object logo = cuerpo.get("logo");
        return marcaService.guardarLogo(nombre, logo == null ? null : String.valueOf(logo));
    }

    @DeleteMapping("/{nombre}/logo")
    public Map<String, Object> borrarLogo(@PathVariable String nombre) {
        return marcaService.borrarLogo(nombre);
    }

    @GetMapping("/{nombre}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable String nombre) {
        MarcaService.Imagen imagen = marcaService.logo(nombre);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imagen.tipo()))
                .cacheControl(CacheControl.noCache())
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                .body(imagen.contenido());
    }
}
