package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface mt_NotificacionRepository extends JpaRepository<mt_Notificacion, Long> {
    List<mt_Notificacion> findByUsuarioDestino_IdOrderByFechaCreacionDesc(Integer usuarioId);

    List<mt_Notificacion> findByUsuarioDestino_IdAndLeidaFalseOrderByFechaCreacionDesc(Integer usuarioId);

    long countByUsuarioDestino_IdAndLeidaFalse(Integer usuarioId);

    boolean existsByTarea_IdAndUsuarioDestino_Id(Long tareaId, Integer usuarioId);

    /** Destinatario de la notificación, para comprobar la propiedad antes de modificarla. */
    @Query("SELECT n.usuarioDestino.id FROM mt_Notificacion n WHERE n.id = :id")
    Optional<Integer> findUsuarioDestinoIdById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE mt_Notificacion n SET n.leida = true WHERE n.usuarioDestino.id = :usuarioId AND n.leida = false")
    void marcarTodasComoLeidasByUsuarioId(@Param("usuarioId") Integer usuarioId);

    /** Avisos de una tarea que se elimina (la FK fk_notif_tarea no borra en cascada). */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM mt_Notificacion n WHERE n.tarea.id = :tareaId")
    int deleteByTareaId(@Param("tareaId") Long tareaId);
}
