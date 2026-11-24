package api.aura.controller.mcas;

import api.aura.entity.mcas.McasProductoComercial;
import api.aura.repository.mcas.McasProductoComercialRepository;
import api.aura.service.mcas.engine.McasTexto;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcas/productos-comerciales")
public class McasProductosController {

    private final McasProductoComercialRepository repo;
    public McasProductosController(McasProductoComercialRepository r) { this.repo = r; }

    /**
     * Con filtros devuelve solo productos activos (selección en contratos). Sin
     * filtros devuelve el catálogo completo, incluidos los dados de baja, para que
     * Configuración MCAS pueda reactivarlos; el formulario de contratos ya
     * descarta los inactivos.
     */
    @GetMapping
    public ResponseEntity<List<McasProductoComercial>> listar(
            @RequestParam(required = false) Integer idComercializadora,
            @RequestParam(required = false) String tipoPrecio,
            @RequestParam(required = false) String tipoSuministro) {
        if (idComercializadora != null && tipoPrecio != null && tipoSuministro != null)
            return ResponseEntity.ok(repo.findByIdComercializadoraAndTipoPrecioAndTipoSuministroAndActivoTrue(idComercializadora, tipoPrecio, tipoSuministro));
        if (idComercializadora != null)
            return ResponseEntity.ok(repo.findByIdComercializadoraAndActivoTrue(idComercializadora));
        return ResponseEntity.ok(repo.findAll(Sort.by("idComercializadora", "codigo")));
    }

    /** Sin código, se genera a partir del nombre («Plan Online» ⇒ PLAN_ONLINE; PLAN_ONLINE_2 si ya existe). */
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody McasProductoComercial p) {
        String error = validar(p);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));
        p.setId(null); if (p.getActivo() == null) p.setActivo(true);
        p.setNombre(p.getNombre().trim());
        p.setCodigo(vacio(p.getCodigo()) ? codigoDesdeNombre(p.getIdComercializadora(), p.getNombre()) : p.getCodigo().trim());
        return ResponseEntity.ok(repo.save(p));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody McasProductoComercial datos) {
        String error = validar(datos);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));
        return repo.findById(id).map(p -> {
            p.setIdComercializadora(datos.getIdComercializadora()); p.setIdGestora(datos.getIdGestora());
            // Vacío conserva el que tenía: las reglas buscan el producto por su código
            if (!vacio(datos.getCodigo())) p.setCodigo(datos.getCodigo().trim());
            p.setNombre(datos.getNombre().trim());
            p.setTipoPrecio(datos.getTipoPrecio()); p.setTipoSuministro(datos.getTipoSuministro());
            p.setCodigoZona(datos.getCodigoZona()); p.setCodigoGo(datos.getCodigoGo());
            p.setActivoDesde(datos.getActivoDesde()); p.setActivoHasta(datos.getActivoHasta());
            p.setActivo(datos.getActivo() != null ? datos.getActivo() : Boolean.TRUE);
            return ResponseEntity.ok(repo.save(p));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        McasProductoComercial producto = repo.findById(id).orElse(null);
        if (producto == null) return ResponseEntity.notFound().build();
        producto.setActivo(false);
        repo.save(producto);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Motivo por el que no se puede guardar (con las etiquetas del formulario), o null. El código puede ir vacío. */
    private String validar(McasProductoComercial p) {
        if (p.getIdComercializadora() == null || p.getIdComercializadora() == 0)
            return "Elija la comercializadora del producto.";
        if (vacio(p.getNombre())) return "Indique el nombre del producto.";
        if (!List.of("FIJO", "INDEXADO").contains(p.getTipoPrecio()))
            return "Elija el tipo de precio del producto: fijo o indexado.";
        if (!List.of("LUZ", "GAS").contains(p.getTipoSuministro()))
            return "Elija el tipo de suministro del producto: luz o gas.";
        if (p.getActivoDesde() != null && p.getActivoHasta() != null
                && p.getActivoHasta().isBefore(p.getActivoDesde()))
            return "La fecha «Activo hasta» no puede ser anterior a «Activo desde».";
        return null;
    }

    /** Código a partir del nombre, único en la comercializadora (sufijo _2, _3…; máx. 100 caracteres). */
    private String codigoDesdeNombre(Integer idComercializadora, String nombre) {
        String base = McasTexto.codigo(nombre);
        if (base.isEmpty()) base = "PRODUCTO";
        if (base.length() > 95) base = base.substring(0, 95);
        String codigo = base;
        for (int n = 2; repo.existsByIdComercializadoraAndCodigo(idComercializadora, codigo); n++) codigo = base + "_" + n;
        return codigo;
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
