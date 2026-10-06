package api.aura.repository.mcas;

import api.aura.entity.mcas.McasProductoComercial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface McasProductoComercialRepository extends JpaRepository<McasProductoComercial, Integer> {
    List<McasProductoComercial> findByActivoTrue();
    List<McasProductoComercial> findByIdComercializadoraAndActivoTrue(Integer idComercializadora);
    List<McasProductoComercial> findByIdComercializadoraAndTipoPrecioAndTipoSuministroAndActivoTrue(
            Integer idComercializadora, String tipoPrecio, String tipoSuministro);
}
