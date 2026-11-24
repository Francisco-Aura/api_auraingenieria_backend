package api.aura.repository.mcas;

import api.aura.entity.mcas.McasPoliticaRetrocesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface McasPoliticaRetrocesionRepository extends JpaRepository<McasPoliticaRetrocesion, Integer> {
    @Query("""
        SELECT p FROM McasPoliticaRetrocesion p
        WHERE p.idComercializadora = :comercializadora
          AND p.estado = 'PUBLICADO'
          AND p.vigenciaDesde <= :fecha
          AND (p.vigenciaHasta IS NULL OR p.vigenciaHasta >= :fecha)
        """)
    List<McasPoliticaRetrocesion> findPublicadasVigentes(
            @Param("comercializadora") Integer comercializadora,
            @Param("fecha") LocalDate fecha);

    List<McasPoliticaRetrocesion> findByIdComercializadoraAndEstado(Integer idComercializadora, String estado);

    Optional<McasPoliticaRetrocesion> findByCodigo(String codigo);

    /** Propuestas que muestran esta política como retrocesión potencial (no se puede borrar). */
    @Query("SELECT COUNT(p) FROM McasPropuesta p WHERE p.idPoliticaRetrocesion = :id")
    long contarPropuestas(@Param("id") Integer idPolitica);
}
