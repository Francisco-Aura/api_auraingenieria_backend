package api.aura.controller.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_Categoria;
import api.aura.repository.modulo_tareas.mt_CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tarea-categorias")
@RequiredArgsConstructor
public class TareaCategoriaController {

    private final mt_CategoriaRepository categoriaRepository;

    // Listar todas las categorías (para administración)
    @GetMapping
    public List<Map<String, Object>> obtenerTodas() {
        return categoriaRepository.findAll().stream()
                .map(this::toMap)
                .toList();
    }

    // Obtener por ID
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> obtenerPorId(@PathVariable Long id) {
        return categoriaRepository.findById(id)
                .map(cat -> ResponseEntity.ok(toMap(cat)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Crear nueva categoría
    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Map<String, Object> datos) {
        mt_Categoria categoria = mt_Categoria.builder()
                .nombre((String) datos.get("nombre"))
                .codigo((String) datos.get("codigo"))
                .descripcion((String) datos.get("descripcion"))
                .activo(parseActivo(datos.get("activo")))
                .build();

        mt_Categoria saved = categoriaRepository.save(categoria);
        return ResponseEntity.ok(toMap(saved));
    }

    // Actualizar categoría
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(@PathVariable Long id,
            @RequestBody Map<String, Object> datos) {
        return categoriaRepository.findById(id)
                .map(categoria -> {
                    if (datos.containsKey("nombre")) {
                        categoria.setNombre((String) datos.get("nombre"));
                    }
                    if (datos.containsKey("codigo")) {
                        categoria.setCodigo((String) datos.get("codigo"));
                    }
                    if (datos.containsKey("descripcion")) {
                        categoria.setDescripcion((String) datos.get("descripcion"));
                    }
                    if (datos.containsKey("activo")) {
                        categoria.setActivo(parseActivo(datos.get("activo")));
                    }
                    mt_Categoria saved = categoriaRepository.save(categoria);
                    return ResponseEntity.ok(toMap(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Eliminar categoría
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (categoriaRepository.existsById(id)) {
            categoriaRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // Convertir entidad a Map para el frontend de administración
    private Map<String, Object> toMap(mt_Categoria categoria) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", categoria.getId());
        map.put("nombre", categoria.getNombre());
        map.put("codigo", categoria.getCodigo());
        map.put("descripcion", categoria.getDescripcion());
        map.put("activo", categoria.getActivo());
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
