package api.aura.repository.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_TareaProyecto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface pl_TareaProyectoRepository extends JpaRepository<pl_TareaProyecto, String> {

    List<pl_TareaProyecto> findByProyectoId(String proyectoId);

    List<pl_TareaProyecto> findByEstadoIn(java.util.Collection<String> estados);

    long countByProyectoId(String proyectoId);

    void deleteByProyectoId(String proyectoId);
}
