package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.ClienteNota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteNotaRepository extends JpaRepository<ClienteNota, Long> {

    @Query("""
            SELECT n
            FROM ClienteNota n
            JOIN FETCH n.usuarioAutor u
            WHERE n.cliente.id = :clienteId
            ORDER BY COALESCE(n.fechaEdicion, n.fechaCreacion) DESC
            """)
    List<ClienteNota> findByClienteIdOrderByFechaCreacionDesc(@Param("clienteId") Integer clienteId);

    Optional<ClienteNota> findByIdAndCliente_Id(Long id, Integer clienteId);
}
