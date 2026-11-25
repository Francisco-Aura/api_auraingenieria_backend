package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_TareaTramo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface mt_TareaTramoRepository extends JpaRepository<mt_TareaTramo, Long> {

    /** Tramo que corre ahora para el usuario (a lo sumo uno), bloqueado hasta el final de la transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM mt_TareaTramo t WHERE t.usuario.id = :usuarioId AND t.fin IS NULL")
    Optional<mt_TareaTramo> buscarAbiertoBloqueando(@Param("usuarioId") Integer usuarioId);

    @Query("SELECT t FROM mt_TareaTramo t JOIN FETCH t.tarea WHERE t.usuario.id = :usuarioId AND t.fin IS NULL")
    Optional<mt_TareaTramo> buscarAbierto(@Param("usuarioId") Integer usuarioId);

    /** Último tramo cerrado del usuario: la tarea que «reanuda». */
    @Query("SELECT t FROM mt_TareaTramo t JOIN FETCH t.tarea WHERE t.usuario.id = :usuarioId AND t.fin IS NOT NULL ORDER BY t.fin DESC, t.id DESC")
    List<mt_TareaTramo> ultimosCerrados(@Param("usuarioId") Integer usuarioId, org.springframework.data.domain.Pageable pagina);

    /** Todos los tramos de una tarea (cerrados y abierto), para sumar su tiempo de cronómetro. */
    @Query("SELECT t FROM mt_TareaTramo t JOIN FETCH t.tarea WHERE t.tarea.id = :tareaId")
    List<mt_TareaTramo> deLaTarea(@Param("tareaId") Long tareaId);

    /** Tramos que se solapan con [desde, hasta) (los abiertos cuentan hasta ahora). */
    @Query("SELECT t FROM mt_TareaTramo t JOIN FETCH t.tarea WHERE t.usuario.id = :usuarioId AND t.inicio < :hasta AND (t.fin IS NULL OR t.fin > :desde) ORDER BY t.inicio")
    List<mt_TareaTramo> solapados(@Param("usuarioId") Integer usuarioId, @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Cierra los tramos abiertos de una tarea (al completarla). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE mt_TareaTramo t SET t.fin = :fin, t.cierre = :cierre WHERE t.tarea.id = :tareaId AND t.fin IS NULL")
    int cerrarAbiertosDeTarea(@Param("tareaId") Long tareaId, @Param("fin") LocalDateTime fin, @Param("cierre") String cierre);
}
