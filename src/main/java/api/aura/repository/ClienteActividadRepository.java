package api.aura.repository;

import api.aura.entity.ClienteActividadEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClienteActividadRepository extends JpaRepository<ClienteActividadEvento, Long> {

    @Query("""
            SELECT a
            FROM ClienteActividadEvento a
            WHERE a.clienteId = :clienteId
              AND (COALESCE(:nivelAcceso, 0) = 1 OR COALESCE(a.soloNivelUno, false) = false)
            ORDER BY a.fechaEvento DESC
            """)
    List<ClienteActividadEvento> findActividadVisibleByCliente(@Param("clienteId") Integer clienteId,
            @Param("nivelAcceso") Integer nivelAcceso, Pageable pageable);
}
