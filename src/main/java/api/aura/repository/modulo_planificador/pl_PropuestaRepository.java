package api.aura.repository.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Propuesta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface pl_PropuestaRepository extends JpaRepository<pl_Propuesta, String> {

    List<pl_Propuesta> findByPresupuestoIdOrderByVersionDesc(String presupuestoId);

    List<pl_Propuesta> findByPresupuestoId(String presupuestoId);

    List<pl_Propuesta> findByPresupuestoIdInOrderByVersionAsc(Collection<String> presupuestoIds);

    List<pl_Propuesta> findAllByOrderByVersionAsc();
}
