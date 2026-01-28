package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_TareaHistorial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface mt_TareaHistorialRepository extends JpaRepository<mt_TareaHistorial, Long> {

    Optional<mt_TareaHistorial> findTopByTarea_IdOrderByFechaInicioDesc(Long tareaId);

    List<mt_TareaHistorial> findByTarea_IdOrderByFechaInicioDesc(Long tareaId);
}
