package api.aura.controller.mcas;

import api.aura.entity.mcas.McasProductoComercial;
import api.aura.repository.mcas.McasProductoComercialRepository;
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

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody McasProductoComercial p) {
        String error = validar(p);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));
        p.setId(null); if (p.getActivo() == null) p.setActivo(true);
        return ResponseEntity.ok(repo.save(p));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody McasProductoComercial datos) {
        String error = validar(datos);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));
        return repo.findById(id).map(p -> {
            p.setIdComercializadora(datos.getIdComercializadora()); p.setIdGestora(datos.getIdGestora());
            p.setCodigo(datos.getCodigo()); p.setNombre(datos.getNombre());
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

    private String validar(McasProductoComercial p) {
        if (p.getIdComercializadora() == null) return "La comercializadora es obligatoria";
        if (p.getCodigo() == null || p.getCodigo().isBlank()) return "El codigo es obligatorio";
        if (p.getNombre() == null || p.getNombre().isBlank()) return "El nombre es obligatorio";
        if (!List.of("FIJO", "INDEXADO").contains(p.getTipoPrecio()))
            return "tipoPrecio debe ser FIJO o INDEXADO";
        if (!List.of("LUZ", "GAS").contains(p.getTipoSuministro()))
            return "tipoSuministro debe ser LUZ o GAS";
        if (p.getActivoDesde() != null && p.getActivoHasta() != null
                && p.getActivoHasta().isBefore(p.getActivoDesde()))
            return "activoHasta no puede ser anterior a activoDesde";
        return null;
    }
}
