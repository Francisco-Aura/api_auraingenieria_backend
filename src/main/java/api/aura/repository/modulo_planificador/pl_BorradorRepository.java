package api.aura.repository.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Borrador;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface pl_BorradorRepository extends JpaRepository<pl_Borrador, String> {

    List<pl_Borrador> findAllByOrderByActualizadoElDesc(Pageable limite);
}
