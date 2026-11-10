package api.aura.repository.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Actividad;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface pl_ActividadRepository extends JpaRepository<pl_Actividad, String> {

    List<pl_Actividad> findByProyectoIdOrderByFechaDesc(String proyectoId, Pageable limite);

    void deleteByProyectoId(String proyectoId);
}
