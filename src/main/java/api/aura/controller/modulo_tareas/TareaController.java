package api.aura.controller.modulo_tareas;

import api.aura.dto.modulo_tareas.*;
import api.aura.service.modulo_tareas.TareaService;
import api.aura.service.modulo_tareas.FileStorageService;
import api.aura.entity.modulo_tareas.mt_Adjunto;
import api.aura.repository.modulo_tareas.AdjuntoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tareas")
@RequiredArgsConstructor
public class TareaController {

    private final TareaService tareaService;
    private final FileStorageService fileStorageService;
    private final AdjuntoRepository adjuntoRepository;

    @GetMapping
    public List<TareaDTO> obtenerTodas() {
        return tareaService.obtenerTodas();
    }

    @GetMapping("/usuario/{usuarioId}")
    public List<TareaDTO> obtenerPorUsuario(@PathVariable Integer usuarioId) {
        return tareaService.obtenerPorUsuario(usuarioId);
    }

    @GetMapping("/cliente/{clienteId}")
    public List<TareaDTO> obtenerPorCliente(@PathVariable Integer clienteId) {
        return tareaService.obtenerPorCliente(clienteId);
    }

    @GetMapping("/usuario/{usuarioId}/count")
    public ResponseEntity<?> contarActivas(@PathVariable Integer usuarioId) {
        long count = tareaService.contarActivasPorUsuario(usuarioId);
        return ResponseEntity.ok(new CountResponse(count));
    }

    @GetMapping("/usuario/{usuarioId}/estadisticas")
    public EstadisticasTareasDTO estadisticas(@PathVariable Integer usuarioId,
            @RequestParam(required = false) Integer nivelAcceso) {
        return tareaService.obtenerEstadisticas(usuarioId, nivelAcceso);
    }

    @GetMapping("/{id}")
    public TareaDTO obtenerPorId(@PathVariable Long id) {
        return tareaService.obtenerPorId(id);
    }

    @PostMapping
    public TareaDTO crear(@RequestBody TareaDTO dto) {
        return tareaService.crear(dto);
    }

    @PutMapping("/{id}")
    public TareaDTO actualizar(@PathVariable Long id, @RequestBody TareaDTO dto) {
        return tareaService.actualizar(id, dto);
    }

    @PatchMapping("/{id}/fijada")
    public ResponseEntity<?> toggleFijada(@PathVariable Long id, @RequestBody java.util.Map<String, Boolean> body) {
        Boolean fijada = body.get("fijada");
        if (fijada == null) {
            return ResponseEntity.badRequest().body("Se requiere el campo 'fijada'");
        }
        tareaService.actualizarFijada(id, fijada);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{tareaId}/estado/{estadoId}")
    public TareaDTO cambiarEstado(@PathVariable Long tareaId, @PathVariable Long estadoId) {
        return tareaService.cambiarEstado(tareaId, estadoId);
    }

    @PatchMapping("/{tareaId}/estado")
    public TareaDTO cambiarEstadoAvanzado(@PathVariable Long tareaId, @RequestBody CambioEstadoRequest request) {
        return tareaService.cambiarEstado(tareaId, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        tareaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/vencidas")
    public List<TareaDTO> vencidas() {
        return tareaService.obtenerVencidas();
    }

    @GetMapping("/vencen-hoy")
    public List<TareaDTO> vencenHoy(@RequestParam Integer usuarioId, @RequestParam Integer nivelAcceso) {
        return tareaService.obtenerVencenHoy(usuarioId, nivelAcceso);
    }

    @GetMapping("/por-vencer")
    public List<TareaDTO> porVencer(@RequestParam(defaultValue = "7") int dias) {
        return tareaService.obtenerPorVencer(dias);
    }

    @GetMapping("/estados")
    public List<EstadoTareaDTO> estados() {
        return tareaService.obtenerEstados(false);
    }

    @GetMapping("/estados/no-finales")
    public List<EstadoTareaDTO> estadosNoFinales() {
        return tareaService.obtenerEstados(true);
    }

    @GetMapping("/categorias")
    public List<CategoriaDTO> categorias() {
        return tareaService.obtenerCategorias();
    }

    @GetMapping("/categorias/{id}")
    public CategoriaDTO categoriaPorId(@PathVariable Long id) {
        return tareaService.obtenerCategoria(id);
    }

    @GetMapping("/categorias/{id}/subcategorias")
    public List<SubCategoriaDTO> subPorCategoria(@PathVariable Long id) {
        return tareaService.obtenerSubCategoriasPorCategoria(id);
    }

    @GetMapping("/categorias/subcategorias")
    public List<SubCategoriaDTO> todasSubcategorias() {
        return tareaService.obtenerTodasSubcategorias();
    }

    @GetMapping("/{id}/historial")
    public List<TareaHistorialDTO> historial(@PathVariable Long id) {
        return tareaService.obtenerHistorial(id);
    }

    @PostMapping("/{tareaId}/adjuntos")
    public ResponseEntity<Map<String, Object>> subirAdjunto(
            @PathVariable Long tareaId,
            @RequestParam("file") MultipartFile file) {
        try {
            mt_Adjunto adjunto = tareaService.agregarAdjuntoDirecto(tareaId, file);
            Map<String, Object> response = new HashMap<>();
            response.put("id", adjunto.getId());
            response.put("nombreArchivo", adjunto.getNombreArchivo());
            response.put("tamanio", adjunto.getTamanio());
            response.put("tipoMime", adjunto.getTipoMime());
            response.put("fechaSubida", adjunto.getFechaSubida());
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{tareaId}/adjuntos")
    public ResponseEntity<List<Map<String, Object>>> obtenerAdjuntos(@PathVariable Long tareaId) {
        List<mt_Adjunto> adjuntos = adjuntoRepository.findByTareaIdAndComentarioIsNull(tareaId);
        List<Map<String, Object>> response = adjuntos.stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("nombreArchivo", a.getNombreArchivo());
            m.put("tamanio", a.getTamanio());
            m.put("tipoMime", a.getTipoMime());
            m.put("fechaSubida", a.getFechaSubida());
            return m;
        }).toList();
        return ResponseEntity.ok(response);
    }

    private record CountResponse(long count) {
    }
}
