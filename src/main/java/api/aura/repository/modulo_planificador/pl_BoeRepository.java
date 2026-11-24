package api.aura.repository.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Boe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface pl_BoeRepository extends JpaRepository<pl_Boe, Integer> {

    /** Del más reciente al más antiguo; a igual fecha, el último que se añadió. */
    List<pl_Boe> findAllByOrderByVigenteDesdeDescIdDesc();
}
