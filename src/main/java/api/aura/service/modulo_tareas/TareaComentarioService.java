package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.ComentarioDTO;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Adjunto;
import api.aura.entity.modulo_tareas.mt_Comentario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.AdjuntoRepository;
import api.aura.repository.modulo_tareas.mt_ComentarioRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TareaComentarioService {

        private final mt_ComentarioRepository comentarioRepository;
        private final mt_TareaRepository tareaRepository;
        private final UsuarioRepository usuarioRepository;
        private final TareaNotificacionService notificacionService;
        private final AdjuntoRepository adjuntoRepository;
        private final FileStorageService fileStorageService;

        public List<ComentarioDTO> obtenerPorTarea(Long tareaId) {
                List<mt_Comentario> comentarios = comentarioRepository.findByTarea_IdOrderByFechaCreacionAsc(tareaId);
                // Ordenar: primero fijados, luego por fecha descendente (más reciente primero)
                comentarios.sort((a, b) -> {
                        // Fijados primero
                        if (Boolean.TRUE.equals(a.getFijado()) && !Boolean.TRUE.equals(b.getFijado()))
                                return -1;
                        if (!Boolean.TRUE.equals(a.getFijado()) && Boolean.TRUE.equals(b.getFijado()))
                                return 1;
                        // Luego por fecha descendente (más reciente primero)
                        return b.getFechaCreacion().compareTo(a.getFechaCreacion());
                });
                return comentarios.stream().map(this::toDto).collect(Collectors.toList());
        }

        public ComentarioDTO crearComentario(ComentarioDTO dto) {
                mt_Tarea tarea = tareaRepository.findById(dto.getTareaId())
                                .orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada"));
                Usuario usuario = usuarioRepository.findById(dto.getUsuarioId())
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

                mt_Comentario comentario = mt_Comentario.builder()
                                .tarea(tarea)
                                .usuarioAutor(usuario)
                                .comentario(dto.getComentario())
                                .fijado(false)
                                .build();
                comentarioRepository.save(comentario);

                notificacionService.notificarComentario(tarea, usuario, dto.getComentario());
                return toDto(comentario);
        }

        public ComentarioDTO actualizarComentario(Long id, String nuevoComentario, Integer usuarioIdActual) {
                if (nuevoComentario == null || nuevoComentario.trim().isEmpty()) {
                        throw new IllegalArgumentException("El comentario no puede estar vacío");
                }
                mt_Comentario comentario = comentarioRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Comentario no encontrado"));
                
                // Validar que el usuario sea el autor
                if (!comentario.getUsuarioAutor().getId().equals(usuarioIdActual)) {
                        throw new IllegalArgumentException("No tienes permiso para editar este comentario. Solo el autor puede editarlo.");
                }
                
                comentario.setComentario(nuevoComentario);
                comentario.setFechaEdicion(java.time.LocalDateTime.now());
                comentario.setEditado(true);
                return toDto(comentarioRepository.save(comentario));
        }

        public ComentarioDTO toggleFijado(Long id) {
                mt_Comentario comentario = comentarioRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Comentario no encontrado"));
                comentario.setFijado(!Boolean.TRUE.equals(comentario.getFijado()));
                return toDto(comentarioRepository.save(comentario));
        }

        public void eliminarComentario(Long id, Integer usuarioIdActual) {
                mt_Comentario comentario = comentarioRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Comentario no encontrado"));
                
                // Obtener el usuario actual para verificar su nivel de acceso
                Usuario usuarioActual = usuarioRepository.findById(usuarioIdActual)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
                
                // Verificar si es administrador (nivelAcceso = 1)
                if (usuarioActual.getNivelAcceso() == null || !usuarioActual.getNivelAcceso().equals(1)) {
                        throw new IllegalArgumentException("No tienes permiso para eliminar comentarios. Solo administradores pueden eliminarlos.");
                }
                
                comentarioRepository.deleteById(id);
        }

        public mt_Adjunto agregarAdjunto(Long comentarioId, MultipartFile file) throws IOException {
                mt_Comentario comentario = comentarioRepository.findById(comentarioId)
                                .orElseThrow(() -> new IllegalArgumentException("Comentario no encontrado"));

                mt_Adjunto adjunto = fileStorageService.guardarAdjunto(file, comentario);
                return adjuntoRepository.save(adjunto);
        }

        public mt_Adjunto obtenerAdjunto(Long adjuntoId) {
                return adjuntoRepository.findById(adjuntoId)
                                .orElseThrow(() -> new IllegalArgumentException("Adjunto no encontrado"));
        }

        public void eliminarAdjunto(Long adjuntoId) throws IOException {
                mt_Adjunto adjunto = adjuntoRepository.findById(adjuntoId)
                                .orElseThrow(() -> new IllegalArgumentException("Adjunto no encontrado"));

                // Eliminar archivo físico
                fileStorageService.eliminarArchivo(adjunto.getRutaArchivo());

                // Eliminar registro de base de datos
                adjuntoRepository.delete(adjunto);
        }

        public mt_Adjunto actualizarAdjunto(Long adjuntoId, MultipartFile file) throws IOException {
                mt_Adjunto adjunto = adjuntoRepository.findById(adjuntoId)
                                .orElseThrow(() -> new IllegalArgumentException("Adjunto no encontrado"));

                // Eliminar archivo físico anterior
                fileStorageService.eliminarArchivo(adjunto.getRutaArchivo());

                // Guardar nuevo archivo
                mt_Comentario comentario = adjunto.getComentario();
                mt_Adjunto nuevoAdjunto = fileStorageService.guardarAdjunto(file, comentario);

                // Actualizar datos del adjunto existente
                adjunto.setNombreArchivo(nuevoAdjunto.getNombreArchivo());
                adjunto.setRutaArchivo(nuevoAdjunto.getRutaArchivo());
                adjunto.setTipoMime(nuevoAdjunto.getTipoMime());
                adjunto.setTamanio(nuevoAdjunto.getTamanio());

                return adjuntoRepository.save(adjunto);
        }

        private ComentarioDTO toDto(mt_Comentario comentario) {
                List<ComentarioDTO.AdjuntoDTO> adjuntosDTO = comentario.getAdjuntos().stream()
                                .map(adj -> ComentarioDTO.AdjuntoDTO.builder()
                                                .id(adj.getId())
                                                .nombreArchivo(adj.getNombreArchivo())
                                                .tamanio(adj.getTamanio())
                                                .tipoMime(adj.getTipoMime())
                                                .fechaSubida(adj.getFechaSubida())
                                                .build())
                                .collect(Collectors.toList());

                return ComentarioDTO.builder()
                                .id(comentario.getId())
                                .tareaId(comentario.getTarea().getId())
                                .usuarioId(comentario.getUsuarioAutor().getId())
                                .usuarioNombre(comentario.getUsuarioAutor().getNombre())
                                .comentario(comentario.getComentario())
                                .fechaCreacion(comentario.getFechaCreacion())
                                .fechaEdicion(comentario.getFechaEdicion())
                                .editado(comentario.getEditado() != null ? comentario.getEditado() : false)
                                .fijado(comentario.getFijado() != null ? comentario.getFijado() : false)
                                .adjuntos(adjuntosDTO)
                                .build();
        }
}
