package api.aura.repository.mcas;

import api.aura.entity.mcas.McasZonaComision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface McasZonaComisionRepository extends JpaRepository<McasZonaComision, Integer> {

    /** Las zonas de la comercializadora (sus provincias y, si la tiene, la del resto). */
    List<McasZonaComision> findByIdComercializadora(Integer idComercializadora);
}
