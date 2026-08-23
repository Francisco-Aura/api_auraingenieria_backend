package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.ClienteNotaAdjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteNotaAdjuntoRepository extends JpaRepository<ClienteNotaAdjunto, Long> {

    List<ClienteNotaAdjunto> findByNota_IdOrderByFechaSubidaAsc(Long notaId);

    List<ClienteNotaAdjunto> findByNota_IdIn(Collection<Long> notaIds);

    Optional<ClienteNotaAdjunto> findByIdAndNota_Id(Long id, Long notaId);

    void deleteByNota_Id(Long notaId);

    @Modifying
    void delete(ClienteNotaAdjunto adjunto);
}
