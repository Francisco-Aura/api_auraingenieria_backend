package api.aura.repository.mcas;

import api.aura.entity.mcas.McasReglaComision;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface McasReglaComisionRepository extends JpaRepository<McasReglaComision, Integer> {
    List<McasReglaComision> findByIdConjuntoReglas(Integer idConjuntoReglas);
    /** Todas las reglas del conjunto, activas e inactivas (listado de administración). */
    List<McasReglaComision> findByIdConjuntoReglasOrderByPrioridadAsc(Integer idConjuntoReglas);
    List<McasReglaComision> findByIdConjuntoReglasAndActivoTrueOrderByPrioridadAsc(Integer idConjuntoReglas);

    /**
     * La regla con bloqueo de escritura hasta el fin de la transacción (SELECT … FOR UPDATE).
     * Serializa los guardados de la tabla completa de una regla: el segundo espera al
     * primero y compara la firma con lo ya confirmado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM McasReglaComision r WHERE r.id = :id")
    Optional<McasReglaComision> findByIdParaActualizar(@Param("id") Integer id);
}
