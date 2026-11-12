package api.aura.controller.modulo_tareas;

import lombok.extern.slf4j.Slf4j;
import api.aura.config.AuraUserHolder;
import api.aura.controller.SesionActual;
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

@Slf4j
@RestController
@RequestMapping("/api/tareas")
@RequiredArgsConstructor
public class TareaController {

    private final TareaService tareaService;
    private final FileStorageService fileStorageService;
    private final AdjuntoRepository adjuntoRepository;

    // Todas las tareas de todos los usuarios: la pantalla solo lo pide para administradores
    @GetMapping
    public List<TareaDTO> obtenerTodas() {
        SesionActual.exigirAdmin();
        return tareaService.obtenerTodas();
    }

    @GetMapping("/usuario/{usuarioId}")
    public List<TareaDTO> obtenerPorUsuario(@PathVariable Integer usuarioId) {
        SesionActual.exigirPropioOAdmin(usuarioId);
        return tareaService.obtenerPorUsuario(usuarioId);
    }

    @GetMapping("/cliente/{clienteId}")
    public List<TareaDTO> obtenerPorCliente(@PathVariable Integer clienteId) {
        return tareaService.obtenerPorCliente(clienteId);
    }

    @GetMapping("/usuario/{usuarioId}/count")
    public ResponseEntity<?> contarActivas(@PathVariable Integer usuarioId) {
        SesionActual.exigirPropioOAdmin(usuarioId);
        long count = tareaService.contarActivasPorUsuario(usuarioId);
        return ResponseEntity.ok(new CountResponse(count));
    }

    @GetMapping("/usuario/{usuarioId}/estadisticas")
    public EstadisticasTareasDTO estadisticas(@PathVariable Integer usuarioId,
            @RequestParam(required = false) Integer nivelAcceso) {
        SesionActual.exigirPropioOAdmin(usuarioId);
        // El nivel es el de la sesión, no el del parámetro (nivelAcceso=1 daba las de todos),
        // y solo para las propias: un administrador que pide las de otro usuario ve las de ese
        boolean propias = usuarioId != null && usuarioId.equals(AuraUserHolder.getId());
        return tareaService.obtenerEstadisticas(usuarioId, propias ? AuraUserHolder.getNivelAcceso() : null);
    }

    @GetMapping("/{id}")
    public TareaDTO obtenerPorId(@PathVariable Long id) {
        exigirVerTarea(id);
        return tareaService.obtenerPorId(id);
    }

    @PostMapping
    public TareaDTO crear(@RequestBody TareaDTO dto) {
        // Creador y autor de la acción son el usuario de la sesión, no los ids del cuerpo
        Integer usuarioSesion = SesionActual.idRequerido();
        dto.setUsuarioCreadorId(usuarioSesion);
        dto.setUsuarioAccionId(usuarioSesion);
        return tareaService.crear(dto);
    }

    @PutMapping("/{id}")
    public TareaDTO actualizar(@PathVariable Long id, @RequestBody TareaDTO dto) {
        exigirVerTarea(id);
        exigirPermisoSobreTarea(id);
        dto.setUsuarioAccionId(SesionActual.idRequerido());
        return tareaService.actualizar(id, dto);
    }

    @PatchMapping("/{id}/fijada")
    public ResponseEntity<?> toggleFijada(@PathVariable Long id, @RequestBody java.util.Map<String, Boolean> body) {
        Boolean fijada = body.get("fijada");
        if (fijada == null) {
            return ResponseEntity.badRequest().body("Se requiere el campo 'fijada'");
        }
        exigirVerTarea(id);
        tareaService.actualizarFijada(id, fijada);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{tareaId}/estado/{estadoId}")
    public TareaDTO cambiarEstado(@PathVariable Long tareaId, @PathVariable Long estadoId) {
        exigirVerTarea(tareaId);
        // El cambio queda a nombre de quien lo hace (antes, del asignado a la tarea)
        CambioEstadoRequest request = new CambioEstadoRequest();
        request.setEstadoId(estadoId);
        request.setUsuarioAccionId(SesionActual.idRequerido());
        return tareaService.cambiarEstado(tareaId, request);
    }

    @PatchMapping("/{tareaId}/estado")
    public TareaDTO cambiarEstadoAvanzado(@PathVariable Long tareaId, @RequestBody CambioEstadoRequest request) {
        exigirVerTarea(tareaId);
        request.setUsuarioAccionId(SesionActual.idRequerido());
        return tareaService.cambiarEstado(tareaId, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        exigirVerTarea(id);
        exigirPermisoSobreTarea(id);
        tareaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Ver o tocar una tarea por su número: solo las que el usuario encuentra en pantalla.
     * Antes solo se comprobaba al leer, y cambiar el estado, editar o eliminar no lo miraba.
     */
    private void exigirVerTarea(Long id) {
        Integer usuario = SesionActual.idRequerido();
        if (!AuraUserHolder.esAdmin() && !tareaService.puedeVerTarea(id, usuario)) {
            throw new SecurityException("No tiene acceso a esta tarea");
        }
    }

    /**
     * Misma regla que la pantalla de Tareas (puedeEditarTarea / puedeEliminarTarea):
     * una tarea creada por un administrador solo la editan o eliminan administradores.
     */
    private void exigirPermisoSobreTarea(Long id) {
        SesionActual.idRequerido();
        if (!AuraUserHolder.esAdmin() && tareaService.creadaPorAdministrador(id)) {
            throw new SecurityException("Solo un administrador puede modificar o eliminar una tarea creada por un administrador");
        }
    }

    @GetMapping("/vencidas")
    public List<TareaDTO> vencidas() {
        SesionActual.exigirAdmin();
        return tareaService.obtenerVencidas();
    }

    @GetMapping("/vencen-hoy")
    public List<TareaDTO> vencenHoy(@RequestParam(required = false) Integer usuarioId,
            @RequestParam(required = false) Integer nivelAcceso) {
        // Usuario y nivel de la sesión; los parámetros se ignoran (con nivelAcceso=1
        // cualquiera veía las tareas de todos)
        return tareaService.obtenerVencenHoy(SesionActual.idRequerido(), AuraUserHolder.getNivelAcceso());
    }

    @GetMapping("/por-vencer")
    public List<TareaDTO> porVencer(@RequestParam(defaultValue = "7") int dias) {
        SesionActual.exigirAdmin();
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
        exigirVerTarea(id);
        return tareaService.obtenerHistorial(id);
    }

    @PostMapping("/{tareaId}/adjuntos")
    public ResponseEntity<Map<String, Object>> subirAdjunto(
            @PathVariable Long tareaId,
            @RequestParam("file") MultipartFile file) {
        // Misma regla que editar la tarea: si la creó un administrador, solo administradores
        exigirVerTarea(tareaId);
        exigirPermisoSobreTarea(tareaId);
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
            log.error("Error de disco con un adjunto", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{tareaId}/adjuntos")
    public ResponseEntity<List<Map<String, Object>>> obtenerAdjuntos(@PathVariable Long tareaId) {
        exigirVerTarea(tareaId);
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
