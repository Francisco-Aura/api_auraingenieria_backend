package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_Comentario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface mt_ComentarioRepository extends JpaRepository<mt_Comentario, Long> {
    List<mt_Comentario> findByTarea_IdOrderByFechaCreacionAsc(Long tareaId);
}
