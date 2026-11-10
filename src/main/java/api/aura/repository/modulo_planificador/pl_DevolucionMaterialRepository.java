package api.aura.repository.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_DevolucionMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface pl_DevolucionMaterialRepository extends JpaRepository<pl_DevolucionMaterial, String> {

    List<pl_DevolucionMaterial> findByProyectoIdOrderByFechaDesc(String proyectoId);

    void deleteByProyectoId(String proyectoId);
}
