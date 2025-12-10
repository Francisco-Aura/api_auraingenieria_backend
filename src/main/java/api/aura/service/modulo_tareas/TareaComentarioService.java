package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.ComentarioDTO;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Comentario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.mt_ComentarioRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public List<ComentarioDTO> obtenerPorTarea(Long tareaId) {
        return comentarioRepository.findByTarea_IdOrderByFechaCreacionAsc(tareaId)
                .stream().map(this::toDto).collect(Collectors.toList());
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
                .build();
        comentarioRepository.save(comentario);

        notificacionService.notificarComentario(tarea, usuario, dto.getComentario());
        return toDto(comentario);
    }

    public ComentarioDTO actualizarComentario(Long id, String nuevoComentario) {
        if (nuevoComentario == null || nuevoComentario.trim().isEmpty()) {
            throw new IllegalArgumentException("El comentario no puede estar vacío");
        }
        mt_Comentario comentario = comentarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Comentario no encontrado"));
        comentario.setComentario(nuevoComentario);
        return toDto(comentarioRepository.save(comentario));
    }

    public void eliminarComentario(Long id) {
        comentarioRepository.deleteById(id);
    }

    private ComentarioDTO toDto(mt_Comentario comentario) {
        return ComentarioDTO.builder()
                .id(comentario.getId())
                .tareaId(comentario.getTarea().getId())
                .usuarioId(comentario.getUsuarioAutor().getId())
                .usuarioNombre(comentario.getUsuarioAutor().getNombre())
                .comentario(comentario.getComentario())
                .fechaCreacion(comentario.getFechaCreacion())
                .build();
    }
}
