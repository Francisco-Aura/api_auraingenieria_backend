package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_TareaEstado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface mt_TareaEstadoRepository extends JpaRepository<mt_TareaEstado, Long> {
    List<mt_TareaEstado> findByEstadoFinalFalseOrderByOrdenAsc();

    List<mt_TareaEstado> findAllByOrderByOrdenAsc();
}
