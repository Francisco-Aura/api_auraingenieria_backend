package api.aura.repository.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface pl_ProyectoRepository extends JpaRepository<pl_Proyecto, String> {

    List<pl_Proyecto> findByEliminadoElIsNullOrderByCreadoElAsc();
}
