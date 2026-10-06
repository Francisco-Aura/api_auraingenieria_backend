package api.aura.repository.mcas;

import api.aura.entity.mcas.McasLineaPropuesta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface McasLineaPropuestaRepository extends JpaRepository<McasLineaPropuesta, Long> {
    List<McasLineaPropuesta> findByIdPropuestaOrderByOrdenAsc(Long idPropuesta);
}
