package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_SubCategoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface mt_SubCategoriaRepository extends JpaRepository<mt_SubCategoria, Long> {
    List<mt_SubCategoria> findByActivoTrueOrderByNombreAsc();
}
