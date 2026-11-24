package api.aura.controller.modulo_planificador;

import api.aura.dto.modulo_planificador.EquipoCrearRequest;
import api.aura.dto.modulo_planificador.EquipoDTO;
import api.aura.service.modulo_planificador.EquipoService;
import api.aura.service.modulo_planificador.RevisionPreciosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/planificador/equipment")
@RequiredArgsConstructor
public class EquipoController {

    private final EquipoService equipoService;
    private final RevisionPreciosService revisionPrecios;

    @GetMapping
    public Map<String, Object> catalogo() {
        return equipoService.catalogo();
    }

    /** El inventario completo en Excel. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportar() {
        Map.Entry<String, byte[]> excel = equipoService.exportarExcel();
        String nombre = java.net.URLEncoder.encode(excel.getKey(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + nombre).body(excel.getValue());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EquipoDTO crear(@RequestBody @Valid EquipoCrearRequest cuerpo) {
        return equipoService.crear(cuerpo);
    }

    @PatchMapping("/{id}")
    public EquipoDTO actualizar(@PathVariable String id, @RequestBody Map<String, Object> cambios) {
        return equipoService.actualizar(id, new LinkedHashMap<>(cambios));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String id) {
        equipoService.eliminar(id);
    }

    @GetMapping("/{id}/price-history")
    public List<Map<String, Object>> historialPrecios(@PathVariable String id) {
        return equipoService.historialPrecios(id);
    }

    @GetMapping("/price-review-status")
    public Map<String, Object> estadoRevision() {
        return revisionPrecios.estado(LocalDate.now());
    }

    @PostMapping("/price-review")
    @SuppressWarnings("unchecked")
    public Map<String, Object> marcarRevisados(@RequestBody Map<String, Object> cuerpo) {
        Object ids = cuerpo.get("ids");
        List<String> lista = ids instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
        return revisionPrecios.marcarRevisados(lista, LocalDate.now());
    }

    // Fichas técnicas: se guardan en Dropbox, en la carpeta de la marca del producto

    @PutMapping("/{id}/datasheet")
    public EquipoDTO subirFicha(@PathVariable String id, @RequestParam String name, @RequestBody(required = false) byte[] contenido) {
        return equipoService.subirFicha(id, name, contenido == null ? new byte[0] : contenido);
    }

    @GetMapping("/{id}/datasheet-options")
    public Map<String, Object> opcionesFicha(@PathVariable String id) {
        return equipoService.opcionesDeFicha(id);
    }

    @GetMapping("/{id}/datasheet")
    public ResponseEntity<byte[]> verFicha(@PathVariable String id) {
        byte[] pdf = equipoService.verFicha(id);
        String nombre = java.net.URLEncoder.encode(equipoService.nombreFichaDe(id), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Content-Disposition", "inline; filename*=UTF-8''" + nombre).body(pdf);
    }

    @DeleteMapping("/{id}/datasheet")
    public EquipoDTO quitarFicha(@PathVariable String id) {
        return equipoService.quitarFicha(id);
    }
}
