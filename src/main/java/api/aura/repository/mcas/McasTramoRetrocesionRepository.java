package api.aura.repository.mcas;

import api.aura.entity.mcas.McasTramoRetrocesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface McasTramoRetrocesionRepository extends JpaRepository<McasTramoRetrocesion, Integer> {
    List<McasTramoRetrocesion> findByIdPoliticaOrderByUnidadAscDesdeValorAscPrioridadAsc(Integer idPolitica);
    /** Tramos de varias políticas en una sola consulta (listados). */
    List<McasTramoRetrocesion> findByIdPoliticaInOrderByUnidadAscDesdeValorAscPrioridadAsc(Collection<Integer> idsPolitica);
    long deleteByIdPolitica(Integer idPolitica);
}
