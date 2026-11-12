package api.aura.repository.mcas;

import api.aura.entity.mcas.McasConjuntoReglas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface McasConjuntoReglasRepository extends JpaRepository<McasConjuntoReglas, Integer> {

    List<McasConjuntoReglas> findByIdComercializadoraAndEstado(Integer idComercializadora, String estado);
    Optional<McasConjuntoReglas> findByCodigo(String codigo);

    /**
     * Busca conjuntos PUBLICADOS vigentes para precio fijo o indexado.
     * faseFiltro debe ser 'FIJO' o 'INDEXADO'; el conjunto aplica si coincide o es 'AMBOS'.
     * Prefiere conjuntos con gestora específica sobre genéricos (filtro en aplicación).
     */
    @Query("""
        SELECT cr FROM McasConjuntoReglas cr
        WHERE cr.idComercializadora = :idComercializadora
          AND (cr.idGestora IS NULL OR cr.idGestora = :idGestora)
          AND cr.estado = 'PUBLICADO'
          AND (cr.fasePrecio = :faseFiltro OR cr.fasePrecio = 'AMBOS')
          AND cr.vigenciaDesde <= :fecha
          AND (cr.vigenciaHasta IS NULL OR cr.vigenciaHasta >= :fecha)
        ORDER BY cr.vigenciaDesde DESC
        """)
    List<McasConjuntoReglas> findPublicadoFijoPorFecha(
            @Param("idComercializadora") Integer idComercializadora,
            @Param("idGestora") Integer idGestora,
            @Param("fecha") LocalDate fecha,
            @Param("faseFiltro") String faseFiltro);
}
