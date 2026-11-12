package api.aura.repository.mcas;

import api.aura.entity.mcas.McasCondicionCobro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface McasCondicionCobroRepository extends JpaRepository<McasCondicionCobro, Integer> {

    /** Las de la comercializadora: las generales (sin gestora) y las de cada gestora. */
    List<McasCondicionCobro> findByIdComercializadora(Integer idComercializadora);
}
