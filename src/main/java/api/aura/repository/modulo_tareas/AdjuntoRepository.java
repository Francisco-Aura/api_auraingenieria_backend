package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_Adjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdjuntoRepository extends JpaRepository<mt_Adjunto, Long> {

    List<mt_Adjunto> findByComentarioId(Long comentarioId);

    List<mt_Adjunto> findByComentario_Tarea_Id(Long tareaId);

    List<mt_Adjunto> findByTareaIdAndComentarioIsNull(Long tareaId);
}
