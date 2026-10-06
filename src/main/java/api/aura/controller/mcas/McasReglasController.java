package api.aura.controller.mcas;

import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasValorRango;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.service.mcas.McasRuleSetValidator;
import api.aura.service.mcas.McasValoresRangoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

@RestController
public class McasReglasController {

    private final McasReglaComisionRepository reglaRepo;
    private final McasValorRangoRepository rangoRepo;
    private final McasConjuntoReglasRepository conjuntoRepo;
    private final McasValoresRangoService tablas;

    public McasReglasController(McasReglaComisionRepository r, McasValorRangoRepository v,
                                McasConjuntoReglasRepository c, McasValoresRangoService tablas) {
        this.reglaRepo = r; this.rangoRepo = v; this.conjuntoRepo = c; this.tablas = tablas;
    }

    /** Cuerpo de PUT …/tabla: la firma de la lectura y todas las filas. */
    public record CambioTabla(String firma, List<McasValorRango> valores) {}

    /** Solo en BORRADOR. Un código vacío conserva el que tenía. */
    @PutMapping("/api/mcas/reglas/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody McasReglaComision datos) {
        return reglaRepo.findById(id).map(r -> {
            if (!editable(r)) return ResponseEntity.badRequest().<Object>body(inmutable());
            if (datos.getCodigo() != null && !datos.getCodigo().isBlank()) r.setCodigo(datos.getCodigo().trim());
            r.setNombre(limpio(datos.getNombre())); r.setDescripcion(limpio(datos.getDescripcion()));
            r.setPrioridad(datos.getPrioridad());
            r.setMetodoCalculo(datos.getMetodoCalculo()); r.setCondicionesJson(datos.getCondicionesJson());
            r.setCalculoJson(datos.getCalculoJson()); r.setPoliticaPagoJson(datos.getPoliticaPagoJson());
            r.setMotivoManual(datos.getMotivoManual()); r.setLocalizadorOrigen(datos.getLocalizadorOrigen());
            r.setActivo(datos.getActivo() != null ? datos.getActivo() : Boolean.TRUE);
            return ResponseEntity.ok(reglaRepo.save(r));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/mcas/reglas/{id}")
    @Transactional
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        McasReglaComision regla = reglaRepo.findById(id).orElse(null);
        if (regla == null) return ResponseEntity.notFound().build();
        if (!editable(regla)) return ResponseEntity.badRequest().body(inmutable());
        rangoRepo.deleteByIdRegla(id);
        reglaRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * Tabla completa de la regla para el editor en rejilla:
     * {@code {firma, metodo, valores}}. La firma se devuelve en el PUT.
     */
    @GetMapping("/api/mcas/reglas/{idRegla}/tabla")
    public ResponseEntity<?> tabla(@PathVariable Integer idRegla) {
        return responder(() -> tablas.leer(idRegla));
    }

    /**
     * Sustituye la tabla entera (solo BORRADOR). 409 si la firma ya no
     * coincide; 400 con {@code incidencias} (con el orden de la fila recibida)
     * si no valida. Se guarda con el orden renumerado 1..n.
     */
    @PutMapping("/api/mcas/reglas/{idRegla}/tabla")
    public ResponseEntity<?> guardarTabla(@PathVariable Integer idRegla, @RequestBody CambioTabla cambio) {
        return responder(() -> tablas.reemplazar(idRegla, cambio.firma(), cambio.valores()));
    }

    @GetMapping("/api/mcas/reglas/{idRegla}/valores-rango")
    public ResponseEntity<List<McasValorRango>> valores(@PathVariable Integer idRegla) {
        return ResponseEntity.ok(rangoRepo.findByIdReglaOrderByOrdenAsc(idRegla));
    }

    @PostMapping("/api/mcas/reglas/{idRegla}/valores-rango")
    public ResponseEntity<?> crearValor(@PathVariable Integer idRegla, @RequestBody McasValorRango v) {
        McasReglaComision regla = reglaRepo.findById(idRegla).orElse(null);
        if (regla == null) return ResponseEntity.notFound().build();
        if (!editable(regla)) return ResponseEntity.badRequest().body(inmutable());
        v.setId(null); v.setIdRegla(idRegla);
        return ResponseEntity.ok(rangoRepo.save(v));
    }

    @PutMapping("/api/mcas/valores-rango/{id}")
    public ResponseEntity<?> actualizarValor(@PathVariable Integer id, @RequestBody McasValorRango datos) {
        return rangoRepo.findById(id).map(v -> {
            McasReglaComision regla = reglaRepo.findById(v.getIdRegla()).orElse(null);
            if (regla == null || !editable(regla)) return ResponseEntity.badRequest().<Object>body(inmutable());
            v.setDimension(datos.getDimension()); v.setValorMin(datos.getValorMin()); v.setValorMax(datos.getValorMax());
            v.setMinInclusivo(datos.getMinInclusivo()); v.setMaxInclusivo(datos.getMaxInclusivo());
            v.setValorCategoria(datos.getValorCategoria()); v.setValorResultado(datos.getValorResultado());
            v.setUnidadResultado(datos.getUnidadResultado()); v.setOrden(datos.getOrden());
            return ResponseEntity.ok(rangoRepo.save(v));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/mcas/valores-rango/{id}")
    public ResponseEntity<?> eliminarValor(@PathVariable Integer id) {
        McasValorRango valor = rangoRepo.findById(id).orElse(null);
        if (valor == null) return ResponseEntity.notFound().build();
        McasReglaComision regla = reglaRepo.findById(valor.getIdRegla()).orElse(null);
        if (regla == null || !editable(regla)) return ResponseEntity.badRequest().body(inmutable());
        rangoRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private boolean editable(McasReglaComision regla) {
        return conjuntoRepo.findById(regla.getIdConjuntoReglas())
                .map(c -> "BORRADOR".equals(c.getEstado())).orElse(false);
    }

    private Map<String, String> inmutable() {
        return Map.of("error", "Solo se pueden cambiar las reglas de una tabla en borrador; "
                + "cree una nueva versión para cambiarlas.");
    }

    private ResponseEntity<?> responder(Supplier<Object> accion) {
        try {
            return ResponseEntity.ok(accion.get());
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (McasValoresRangoService.FirmaObsoletaException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (McasRuleSetValidator.IncidenciasException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(),
                    "incidencias", e.getIncidencias(), "detalles", e.getDetalles()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
