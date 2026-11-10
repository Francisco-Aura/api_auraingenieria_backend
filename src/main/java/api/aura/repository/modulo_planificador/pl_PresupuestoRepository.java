package api.aura.repository.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Presupuesto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface pl_PresupuestoRepository extends JpaRepository<pl_Presupuesto, String> {

    List<pl_Presupuesto> findAllByOrderByActualizadoElDesc();

    List<pl_Presupuesto> findByEstado(String estado);

    List<pl_Presupuesto> findByOfertaId(String ofertaId);
}
