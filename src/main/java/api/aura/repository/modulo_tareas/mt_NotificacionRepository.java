package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface mt_NotificacionRepository extends JpaRepository<mt_Notificacion, Long> {
    List<mt_Notificacion> findByUsuarioDestino_IdOrderByFechaCreacionDesc(Integer usuarioId);

    List<mt_Notificacion> findByUsuarioDestino_IdAndLeidaFalseOrderByFechaCreacionDesc(Integer usuarioId);

    long countByUsuarioDestino_IdAndLeidaFalse(Integer usuarioId);
}
