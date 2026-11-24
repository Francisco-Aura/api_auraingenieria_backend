package api.aura.repository.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Estudio;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface pl_EstudioRepository extends JpaRepository<pl_Estudio, String> {

    List<pl_Estudio> findAllByOrderByActualizadoElDesc(Pageable limite);
}
