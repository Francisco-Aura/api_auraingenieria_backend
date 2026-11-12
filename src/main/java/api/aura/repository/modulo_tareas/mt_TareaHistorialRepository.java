package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_TareaHistorial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface mt_TareaHistorialRepository extends JpaRepository<mt_TareaHistorial, Long> {

    // A igualdad de fecha (dos cambios en el mismo segundo), el último insertado: si no,
    // se podía cerrar uno ya cerrado y el abierto se quedaba sin fin para siempre
    Optional<mt_TareaHistorial> findTopByTarea_IdOrderByFechaInicioDescIdDesc(Long tareaId);

    List<mt_TareaHistorial> findByTarea_IdOrderByFechaInicioDescIdDesc(Long tareaId);

    /** Comentarios de una tarea que empiezan por el prefijo indicado, del más antiguo al más reciente. */
    @Query("SELECT h.comentario FROM mt_TareaHistorial h WHERE h.tarea.id = :tareaId AND h.comentario LIKE :prefijo ORDER BY h.fechaInicio ASC, h.id ASC")
    List<String> comentariosQueEmpiezanPor(@Param("tareaId") Long tareaId, @Param("prefijo") String prefijo);

    long countByComentario(String comentario);

    /** Historial de una tarea que se elimina (la FK fk_th_tarea no borra en cascada). */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM mt_TareaHistorial h WHERE h.tarea.id = :tareaId")
    int deleteByTareaId(@Param("tareaId") Long tareaId);
}
