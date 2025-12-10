package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface mt_CategoriaRepository extends JpaRepository<mt_Categoria, Long> {
    List<mt_Categoria> findByActivoTrueOrderByNombreAsc();
}
