package api.aura.controller.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_SubCategoria;
import api.aura.repository.modulo_tareas.mt_SubCategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tarea-subcategorias")
@RequiredArgsConstructor
public class TareaSubcategoriaController {

    private final mt_SubCategoriaRepository subcategoriaRepository;

    // Listar todas las subcategorías (para administración)
    @GetMapping
    public List<Map<String, Object>> obtenerTodas() {
        return subcategoriaRepository.findAll().stream()
                .map(this::toMap)
                .toList();
    }

    // Obtener por ID
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> obtenerPorId(@PathVariable Long id) {
        return subcategoriaRepository.findById(id)
                .map(sub -> ResponseEntity.ok(toMap(sub)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Crear nueva subcategoría
    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Map<String, Object> datos) {
        mt_SubCategoria subcategoria = mt_SubCategoria.builder()
                .nombre((String) datos.get("nombre"))
                .codigo((String) datos.get("codigo"))
                .activo(parseActivo(datos.get("activo")))
                .build();

        mt_SubCategoria saved = subcategoriaRepository.save(subcategoria);
        return ResponseEntity.ok(toMap(saved));
    }

    // Actualizar subcategoría
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(@PathVariable Long id,
            @RequestBody Map<String, Object> datos) {
        return subcategoriaRepository.findById(id)
                .map(subcategoria -> {
                    if (datos.containsKey("nombre")) {
                        subcategoria.setNombre((String) datos.get("nombre"));
                    }
                    if (datos.containsKey("codigo")) {
                        subcategoria.setCodigo((String) datos.get("codigo"));
                    }
                    if (datos.containsKey("activo")) {
                        subcategoria.setActivo(parseActivo(datos.get("activo")));
                    }
                    mt_SubCategoria saved = subcategoriaRepository.save(subcategoria);
                    return ResponseEntity.ok(toMap(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Eliminar subcategoría
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (subcategoriaRepository.existsById(id)) {
            subcategoriaRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // Convertir entidad a Map para el frontend de administración
    private Map<String, Object> toMap(mt_SubCategoria subcategoria) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", subcategoria.getId());
        map.put("nombre", subcategoria.getNombre());
        map.put("codigo", subcategoria.getCodigo());
        map.put("activo", subcategoria.getActivo());
        return map;
    }

    // Parsear el campo activo que puede venir como Boolean, String o Number
    private Boolean parseActivo(Object value) {
        if (value == null)
            return true;
        if (value instanceof Boolean)
            return (Boolean) value;
        if (value instanceof Number)
            return ((Number) value).intValue() != 0;
        if (value instanceof String) {
            String str = ((String) value).trim().toLowerCase();
            return "true".equals(str) || "1".equals(str) || "si".equals(str) || "sí".equals(str);
        }
        return true;
    }
}
