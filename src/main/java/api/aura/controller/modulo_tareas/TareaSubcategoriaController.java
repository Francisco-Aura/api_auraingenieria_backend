package api.aura.controller.modulo_tareas;

import api.aura.controller.SesionActual;
import api.aura.entity.modulo_tareas.mt_Categoria;
import api.aura.entity.modulo_tareas.mt_SubCategoria;
import api.aura.repository.modulo_tareas.mt_CategoriaRepository;
import api.aura.repository.modulo_tareas.mt_SubCategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tarea-subcategorias")
@RequiredArgsConstructor
public class TareaSubcategoriaController {

    private final mt_SubCategoriaRepository subcategoriaRepository;
    private final mt_CategoriaRepository categoriaRepository;

    // Listar todas las subcategorías (para administración)
    @GetMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> obtenerTodas() {
        return subcategoriaRepository.findAll().stream()
                .map(this::toMap)
                .toList();
    }

    // Obtener por ID
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> obtenerPorId(@PathVariable Long id) {
        return subcategoriaRepository.findById(id)
                .map(sub -> ResponseEntity.ok(toMap(sub)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Crear nueva subcategoría con categorías asociadas
    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Map<String, Object> datos) {
        SesionActual.exigirAdmin();
        mt_SubCategoria subcategoria = mt_SubCategoria.builder()
                .nombre((String) datos.get("nombre"))
                .codigo((String) datos.get("codigo"))
                .activo(parseActivo(datos.get("activo")))
                .build();

        mt_SubCategoria saved = subcategoriaRepository.save(subcategoria);

        // Asociar a las categorías seleccionadas
        if (datos.containsKey("categoriaIds")) {
            asociarCategorias(saved, datos.get("categoriaIds"));
        }

        return ResponseEntity.ok(toMap(saved));
    }

    // Actualizar subcategoría
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> actualizar(@PathVariable Long id,
            @RequestBody Map<String, Object> datos) {
        SesionActual.exigirAdmin();
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

                    // Actualizar relación con categorías
                    if (datos.containsKey("categoriaIds")) {
                        // Primero quitar de todas las categorías actuales
                        for (mt_Categoria cat : new ArrayList<>(subcategoria.getCategorias())) {
                            cat.getSubcategorias().remove(subcategoria);
                            categoriaRepository.save(cat);
                        }
                        // Luego asociar a las nuevas
                        asociarCategorias(subcategoria, datos.get("categoriaIds"));
                    }

                    mt_SubCategoria saved = subcategoriaRepository.save(subcategoria);
                    return ResponseEntity.ok(toMap(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Eliminar subcategoría
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        SesionActual.exigirAdmin();
        return subcategoriaRepository.findById(id)
                .map(subcategoria -> {
                    // Primero quitar de todas las categorías
                    for (mt_Categoria cat : new ArrayList<>(subcategoria.getCategorias())) {
                        cat.getSubcategorias().remove(subcategoria);
                        categoriaRepository.save(cat);
                    }
                    subcategoriaRepository.delete(subcategoria);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Asociar subcategoría a las categorías indicadas
    private void asociarCategorias(mt_SubCategoria subcategoria, Object categoriaIdsObj) {
        List<Long> categoriaIds = parseCategoriaIds(categoriaIdsObj);
        for (Long catId : categoriaIds) {
            categoriaRepository.findById(catId).ifPresent(categoria -> {
                if (!categoria.getSubcategorias().contains(subcategoria)) {
                    categoria.getSubcategorias().add(subcategoria);
                    categoriaRepository.save(categoria);
                }
            });
        }
    }

    // Parsear lista de IDs de categorías
    @SuppressWarnings("unchecked")
    private List<Long> parseCategoriaIds(Object value) {
        if (value == null)
            return List.of();
        if (value instanceof List) {
            return ((List<?>) value).stream()
                    .map(v -> {
                        if (v instanceof Number)
                            return ((Number) v).longValue();
                        if (v instanceof String)
                            return Long.parseLong((String) v);
                        return null;
                    })
                    .filter(v -> v != null)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    // Convertir entidad a Map para el frontend de administración
    private Map<String, Object> toMap(mt_SubCategoria subcategoria) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", subcategoria.getId());
        map.put("nombre", subcategoria.getNombre());
        map.put("codigo", subcategoria.getCodigo());
        map.put("activo", subcategoria.getActivo());

        // Incluir nombres de categorías asociadas
        List<String> categoriasNombres = subcategoria.getCategorias().stream()
                .map(mt_Categoria::getNombre)
                .collect(Collectors.toList());
        map.put("categorias", String.join(", ", categoriasNombres));

        // Incluir IDs de categorías para el formulario de edición
        List<Long> categoriaIds = subcategoria.getCategorias().stream()
                .map(mt_Categoria::getId)
                .collect(Collectors.toList());
        map.put("categoriaIds", categoriaIds);

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
