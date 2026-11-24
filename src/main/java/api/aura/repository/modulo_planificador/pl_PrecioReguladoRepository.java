package api.aura.repository.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_PrecioRegulado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface pl_PrecioReguladoRepository extends JpaRepository<pl_PrecioRegulado, Integer> {

    List<pl_PrecioRegulado> findByBoeIdAndTarifa(Integer boeId, String tarifa);

    List<pl_PrecioRegulado> findByBoeId(Integer boeId);
}
