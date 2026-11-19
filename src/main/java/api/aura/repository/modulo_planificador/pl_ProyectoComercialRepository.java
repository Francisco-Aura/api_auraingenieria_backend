package api.aura.repository.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_ProyectoComercial;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface pl_ProyectoComercialRepository extends JpaRepository<pl_ProyectoComercial, String> {

    List<pl_ProyectoComercial> findAllByOrderByCreadoElDesc(Pageable pagina);

    boolean existsByClave(String clave);
}
