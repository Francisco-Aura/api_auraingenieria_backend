package api.aura.repository.mcas;

import api.aura.entity.mcas.McasCalendarioPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface McasCalendarioPagoRepository extends JpaRepository<McasCalendarioPago, Long> {
    List<McasCalendarioPago> findByIdPropuestaOrderByNumeroPagoAsc(Long idPropuesta);
    void deleteByIdPropuesta(Long idPropuesta);
}
