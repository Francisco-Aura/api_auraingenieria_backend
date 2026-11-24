package api.aura.repository.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_PvgisSerie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface pl_PvgisSerieRepository extends JpaRepository<pl_PvgisSerie, Integer> {

    Optional<pl_PvgisSerie> findByLatCAndLonCAndAnguloAndAzimut(Integer latC, Integer lonC, Integer angulo, Integer azimut);
}
