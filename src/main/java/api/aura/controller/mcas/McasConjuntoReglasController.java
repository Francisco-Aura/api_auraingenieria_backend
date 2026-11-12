package api.aura.controller.mcas;

import api.aura.config.AuraUserHolder;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.service.mcas.McasConjuntoService;
import api.aura.service.mcas.McasRuleSetValidator;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * Tablas de comisiones (conjuntos de reglas) de Configuración MCAS. Solo
 * administrador (McasAccessInterceptor). El ciclo de vida para el administrador
 * no técnico (nueva versión, comprobar, activar, volver a borrador) está en
 * {@link McasConjuntoService}; validar y publicar siguen disponibles.
 */
@RestController
@RequestMapping("/api/mcas/conjuntos-reglas")
public class McasConjuntoReglasController {

    private final McasConjuntoReglasRepository conjuntoRepo;
    private final McasReglaComisionRepository reglaRepo;
    private final McasValorRangoRepository rangoRepo;
    private final McasRuleSetValidator validator;
    private final McasConjuntoService conjuntos;

    public McasConjuntoReglasController(McasConjuntoReglasRepository c, McasReglaComisionRepository r,
                                        McasValorRangoRepository rangos, McasRuleSetValidator validator,
                                        McasConjuntoService conjuntos) {
        this.conjuntoRepo = c; this.reglaRepo = r; this.rangoRepo = rangos; this.validator = validator;
        this.conjuntos = conjuntos;
    }

    /** Cuerpo de «Nueva versión». */
    public record NuevaVersion(LocalDate vigenciaDesde, String nombre) {}

    /** Cuerpo de «Activar»; sin cuerpo, se cierra la vigencia anterior. */
    public record Activacion(Boolean cerrarVigenciaAnterior) {}

    /** Cuerpo de «Activar en lote». */
    public record Lote(List<Integer> ids, Boolean cerrarVigenciaAnterior) {}

    @GetMapping
    public ResponseEntity<List<McasConjuntoReglas>> listar() { return ResponseEntity.ok(conjuntoRepo.findAll()); }

    @GetMapping("/{id}")
    public ResponseEntity<?> detalle(@PathVariable Integer id) {
        return conjuntoRepo.findById(id).<ResponseEntity<?>>map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Listado de administración: incluye las reglas inactivas para que se puedan
     * revisar, reactivar o eliminar. El motor solo evalúa las activas.
     */
    @GetMapping("/{id}/reglas")
    public ResponseEntity<List<McasReglaComision>> reglas(@PathVariable Integer id) {
        return ResponseEntity.ok(reglaRepo.findByIdConjuntoReglasOrderByPrioridadAsc(id));
    }

    /** Crea en BORRADOR; si el código llega vacío, se genera (COMERCIALIZADORA-FASE-yyyyMMdd). */
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody McasConjuntoReglas c) {
        return responder(() -> conjuntos.crear(c));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody McasConjuntoReglas datos) {
        return conjuntoRepo.findById(id).map(c -> {
            if (!"BORRADOR".equals(c.getEstado()))
                return ResponseEntity.badRequest().<Object>body(Map.of("error",
                        "Solo se puede editar una tabla en borrador; cree una nueva versión para cambiarla."));
            if (datos.getCodigo() != null && !datos.getCodigo().isBlank()) c.setCodigo(datos.getCodigo().trim());
            c.setNombre(limpio(datos.getNombre())); c.setDescripcion(limpio(datos.getDescripcion()));
            c.setVersion(datos.getVersion());
            c.setIdComercializadora(datos.getIdComercializadora()); c.setIdGestora(datos.getIdGestora());
            c.setFasePrecio(datos.getFasePrecio()); c.setVigenciaDesde(datos.getVigenciaDesde());
            c.setVigenciaHasta(datos.getVigenciaHasta()); c.setArchivoOrigen(datos.getArchivoOrigen());
            c.setHojaOrigen(datos.getHojaOrigen()); c.setFechaInternaOrigen(datos.getFechaInternaOrigen());
            return ResponseEntity.ok(conjuntoRepo.save(c));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        return conjuntoRepo.findById(id).map(c -> {
            if (!"BORRADOR".equals(c.getEstado()))
                return ResponseEntity.badRequest().<Object>body(Map.of("error", "Solo se puede eliminar una tabla en borrador."));
            List<McasReglaComision> reglas = reglaRepo.findByIdConjuntoReglas(id);
            reglas.forEach(r -> rangoRepo.deleteByIdRegla(r.getId()));
            reglaRepo.deleteAll(reglas);
            conjuntoRepo.deleteById(id);
            return ResponseEntity.ok().<Object>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/validar")
    public ResponseEntity<?> validar(@PathVariable Integer id) {
        return conjuntoRepo.findById(id).map(c -> {
            if (!"BORRADOR".equals(c.getEstado()))
                return ResponseEntity.badRequest().<Object>body(Map.of("error", "Solo se puede validar una tabla en borrador."));
            String fase = conjuntos.motivoFaseBloqueada(c);
            if (fase != null) return ResponseEntity.badRequest().<Object>body(Map.of("error", fase));
            McasRuleSetValidator.Resultado resultado = validator.validar(c);
            if (!resultado.valido())
                return ResponseEntity.badRequest().<Object>body(Map.of(
                        "error", "La tabla tiene problemas de configuración; corríjalos antes de validarla.",
                        "detalles", resultado.errores(),
                        "incidencias", resultado.incidencias()));
            c.setEstado("VALIDADO");
            return ResponseEntity.ok(conjuntoRepo.save(c));
        })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/publicar")
    public ResponseEntity<?> publicar(@PathVariable Integer id) {
        return conjuntoRepo.findById(id).map(c -> {
            if (!"VALIDADO".equals(c.getEstado()))
                return ResponseEntity.badRequest().<Object>body(Map.of("error", "Solo se puede publicar una tabla validada."));
            String fase = conjuntos.motivoFaseBloqueada(c);
            if (fase != null) return ResponseEntity.badRequest().<Object>body(Map.of("error", fase));
            McasRuleSetValidator.Resultado resultado = validator.validarPublicacion(c);
            if (!resultado.valido())
                return ResponseEntity.badRequest().<Object>body(Map.of(
                        "error", "La tabla no se puede publicar: revise los problemas indicados.",
                        "detalles", resultado.errores(),
                        "incidencias", resultado.incidencias()));
            c.setEstado("PUBLICADO"); c.setAprobadoPor(AuraUserHolder.get()); c.setFechaAprobacion(LocalDateTime.now());
            return ResponseEntity.ok(conjuntoRepo.save(c));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/archivar")
    public ResponseEntity<?> archivar(@PathVariable Integer id) {
        return conjuntoRepo.findById(id).map(c -> {
            if (!"PUBLICADO".equals(c.getEstado()))
                return ResponseEntity.badRequest().<Object>body(Map.of("error", "Solo se puede archivar una tabla activa."));
            c.setEstado("ARCHIVADO");
            return ResponseEntity.ok(conjuntoRepo.save(c));
        })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Fija o adelanta el fin de vigencia de un conjunto validado o publicado (no
     * se pueden editar). Permite publicar una versión nueva a partir del día
     * siguiente sin archivar la anterior, que sigue aplicándose a los contratos
     * que empiezan dentro de su periodo.
     */
    @PostMapping("/{id}/cerrar-vigencia")
    public ResponseEntity<?> cerrarVigencia(@PathVariable Integer id,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return conjuntoRepo.findById(id).map(c -> {
            if (!"VALIDADO".equals(c.getEstado()) && !"PUBLICADO".equals(c.getEstado()))
                return ResponseEntity.badRequest().<Object>body(Map.of("error",
                        "Solo se cierra la vigencia de una tabla validada o activa; un borrador se edita directamente."));
            if (c.getVigenciaDesde() != null && hasta.isBefore(c.getVigenciaDesde()))
                return ResponseEntity.badRequest().<Object>body(Map.of("error",
                        "La fecha de fin no puede ser anterior al inicio de la vigencia."));
            if (c.getVigenciaHasta() != null && hasta.isAfter(c.getVigenciaHasta()))
                return ResponseEntity.badRequest().<Object>body(Map.of("error",
                        "Solo se puede adelantar el fin de la vigencia, no ampliarlo."));
            c.setVigenciaHasta(hasta);
            return ResponseEntity.ok(conjuntoRepo.save(c));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Solo en BORRADOR; si el código llega vacío, se genera a partir del nombre. */
    @PostMapping("/{id}/reglas")
    public ResponseEntity<?> crearRegla(@PathVariable Integer id, @RequestBody McasReglaComision r) {
        return responder(() -> conjuntos.crearRegla(id, r));
    }

    // ============================================================
    // Configurador no técnico (usabilidad 30/09/2026)
    // ============================================================

    /** VALIDADO sin propuestas → BORRADOR. */
    @PostMapping("/{id}/volver-borrador")
    public ResponseEntity<?> volverABorrador(@PathVariable Integer id) {
        return responder(() -> conjuntos.volverABorrador(id));
    }

    /**
     * Copia en BORRADOR (reglas activas e inactivas y sus tramos) desde {@code vigenciaDesde}.
     * Sin cuerpo o sin fecha, 400 con el motivo (no un error genérico).
     */
    @PostMapping("/{id}/nueva-version")
    public ResponseEntity<?> nuevaVersion(@PathVariable Integer id, @RequestBody(required = false) NuevaVersion datos) {
        return responder(() -> conjuntos.nuevaVersion(id, datos == null ? null : datos.vigenciaDesde(),
                datos == null ? null : datos.nombre()));
    }

    /** {@code {puedeActivar, incidencias, cambios, avisos}}; no modifica nada. */
    @GetMapping("/{id}/comprobacion")
    public ResponseEntity<?> comprobacion(@PathVariable Integer id,
                                          @RequestParam(defaultValue = "true") boolean cerrarVigenciaAnterior) {
        return responder(() -> conjuntos.comprobacion(id, cerrarVigenciaAnterior));
    }

    /** Valida, cierra la vigencia anterior y publica en una transacción. */
    @PostMapping("/{id}/activar")
    public ResponseEntity<?> activar(@PathVariable Integer id, @RequestBody(required = false) Activacion datos) {
        return responder(() -> conjuntos.activar(id, cerrar(datos == null ? null : datos.cerrarVigenciaAnterior())));
    }

    /** Una transacción por tabla; resultados parciales. */
    @PostMapping("/activar-lote")
    public ResponseEntity<?> activarLote(@RequestBody Lote lote) {
        return responder(() -> conjuntos.activarLote(lote.ids(), cerrar(lote.cerrarVigenciaAnterior())));
    }

    private static boolean cerrar(Boolean cerrarVigenciaAnterior) {
        return cerrarVigenciaAnterior == null || cerrarVigenciaAnterior;
    }

    private ResponseEntity<?> responder(Supplier<Object> accion) {
        try {
            return ResponseEntity.ok(accion.get());
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
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
