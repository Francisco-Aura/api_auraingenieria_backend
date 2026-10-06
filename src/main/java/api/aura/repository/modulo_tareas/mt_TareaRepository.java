package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_Tarea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface mt_TareaRepository extends JpaRepository<mt_Tarea, Long> {

    List<mt_Tarea> findByUsuarioAsignado_Id(Integer usuarioId);


    List<mt_Tarea> findByClienteRelacionado_Id(Integer clienteId);

    long countByUsuarioAsignado_IdAndEstado_EstadoFinalFalse(Integer usuarioId);

    List<mt_Tarea> findByEstado_EstadoFinalFalseAndFechaVencimientoBefore(LocalDateTime fecha);

    List<mt_Tarea> findByEstado_EstadoFinalFalseAndFechaVencimientoBetween(LocalDateTime desde, LocalDateTime hasta);

    List<mt_Tarea> findByUsuarioAsignado_IdAndEstado_EstadoFinalFalseAndFechaVencimientoBetween(Integer usuarioId, LocalDateTime desde, LocalDateTime hasta);

    // Tareas con notificación activa que vencen en la próxima hora y no han sido
    // notificadas aún
    @Query("SELECT t FROM mt_Tarea t WHERE t.notificar = true " +
            "AND t.estado.estadoFinal = false " +
            "AND t.fechaVencimiento BETWEEN :desde AND :hasta " +
            "AND (t.notificadoProximoVencimiento = false OR t.notificadoProximoVencimiento IS NULL)")
    List<mt_Tarea> findTareasParaNotificarVencimiento(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    // «Repetir»: ¿existe ya la siguiente repetición (mismo título, cliente y vencimiento)?
    @Query("""
            SELECT COUNT(t) FROM mt_Tarea t
            WHERE t.id <> :idOrigen
              AND t.titulo = :titulo
              AND t.fechaVencimiento = :fechaVencimiento
              AND ((:clienteId IS NULL AND t.clienteRelacionado IS NULL) OR t.clienteRelacionado.id = :clienteId)
            """)
    long contarRepeticiones(@Param("idOrigen") Long idOrigen, @Param("titulo") String titulo,
            @Param("fechaVencimiento") LocalDateTime fechaVencimiento, @Param("clienteId") Integer clienteId);

    @Query("""
            SELECT t
            FROM mt_Tarea t
            LEFT JOIN t.subcategoria sc
            WHERE t.clienteRelacionado.id = :clienteId
              AND (
                    UPPER(COALESCE(sc.codigo, '')) = 'INCID'
                    OR UPPER(COALESCE(sc.nombre, '')) LIKE 'INCIDEN%'
              )
            ORDER BY COALESCE(t.fechaVencimiento, t.fechaCreacion) DESC
            """)
    List<mt_Tarea> findIncidenciasByClienteId(@Param("clienteId") Integer clienteId);

    // Reinicio diario de flags de notificación de vencimiento: mismo criterio que el
    // filtrado en Java que sustituye (notificadoProximoVencimiento = true y tarea no
    // finalizada, tratando estado nulo como "no finalizada"), pero como UPDATE directo
    // en lugar de cargar todas las tareas a memoria.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE mt_Tarea t SET t.notificadoProximoVencimiento = false " +
            "WHERE t.notificadoProximoVencimiento = true " +
            "AND (t.estado IS NULL OR t.estado.estadoFinal = false)")
    int reiniciarFlagsNotificacionVencimiento();
}
