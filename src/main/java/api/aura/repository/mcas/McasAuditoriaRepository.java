package api.aura.repository.mcas;

import api.aura.entity.mcas.McasAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface McasAuditoriaRepository extends JpaRepository<McasAuditoria, Long> {
    List<McasAuditoria> findByIdPropuestaOrderByFechaEventoAsc(Long idPropuesta);
    List<McasAuditoria> findByIdContratoOrderByFechaEventoAsc(Integer idContrato);
}
