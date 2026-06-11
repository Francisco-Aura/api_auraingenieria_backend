package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_Tarea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface mt_TareaRepository extends JpaRepository<mt_Tarea, Long> {

    List<mt_Tarea> findByUsuarioAsignado_Id(Integer usuarioId);

    List<mt_Tarea> findByUsuarioAsignado_IdIn(List<Integer> usuariosIds);

    List<mt_Tarea> findByClienteRelacionado_Id(Integer clienteId);

    long countByUsuarioAsignado_IdAndEstado_EstadoFinalFalse(Integer usuarioId);

    List<mt_Tarea> findByEstado_EstadoFinalFalseAndFechaVencimientoBefore(LocalDateTime fecha);

    List<mt_Tarea> findByEstado_EstadoFinalFalseAndFechaVencimientoBetween(LocalDateTime desde, LocalDateTime hasta);

    @Query("SELECT t FROM mt_Tarea t WHERE t.estado.estadoFinal = false AND t.usuarioAsignado.id = :usuarioId")
    List<mt_Tarea> findPendientesByUsuario(@Param("usuarioId") Integer usuarioId);

    // Contadores para estadísticas globales (admin)
    long countByEstado_EstadoFinalFalse();
    long countByEstado_EstadoFinalFalseAndFechaVencimientoBefore(LocalDateTime fecha);
    long countByEstado_CuentaTiempoTrueAndEstado_EstadoFinalFalse();
    long countByEstado_EstadoFinalTrue();
    long countByUsuarioAsignadoIsNullAndEstado_EstadoFinalFalse();

    // Tareas con notificación activa que vencen en la próxima hora y no han sido
    // notificadas aún
    @Query("SELECT t FROM mt_Tarea t WHERE t.notificar = true " +
            "AND t.estado.estadoFinal = false " +
            "AND t.fechaVencimiento BETWEEN :desde AND :hasta " +
            "AND t.notificadoProximoVencimiento = false")
    List<mt_Tarea> findTareasParaNotificarVencimiento(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

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

    @Query("""
            SELECT t
            FROM mt_Tarea t
            WHERE t.clienteRelacionado.id = :clienteId
              AND t.titulo = :titulo
              AND ((:cups IS NULL AND t.cups IS NULL) OR t.cups = :cups)
            ORDER BY t.id DESC
            """)
    List<mt_Tarea> findTareasAnclaNotas(@Param("clienteId") Integer clienteId,
                                        @Param("titulo") String titulo,
                                        @Param("cups") String cups);
}
