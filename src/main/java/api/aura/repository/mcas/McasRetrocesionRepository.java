package api.aura.repository.mcas;

import api.aura.entity.mcas.McasRetrocesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface McasRetrocesionRepository extends JpaRepository<McasRetrocesion, Long> {

    Optional<McasRetrocesion> findByIdPropuesta(Long idPropuesta);

    List<McasRetrocesion> findAllByOrderByCreadoEnDescIdDesc();

    long countByEstado(String estado);

    List<McasRetrocesion> findByIdPuntoSuministro(Integer idPuntoSuministro);
}
