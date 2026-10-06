package api.aura.repository.mcas;

import api.aura.entity.mcas.McasPropuesta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface McasPropuestaRepository extends JpaRepository<McasPropuesta, Long> {

    Optional<McasPropuesta> findByClaveIdempotencia(String claveIdempotencia);
    List<McasPropuesta> findByIdContratoOrderByCreadoEnDesc(Integer idContrato);

    @Query("SELECT p FROM McasPropuesta p WHERE p.idContrato = :id AND p.estado NOT IN ('CANCELADO','SUPERADO') ORDER BY p.creadoEn DESC")
    List<McasPropuesta> findActivasPorContrato(@Param("id") Integer idContrato);

    @Query("SELECT p FROM McasPropuesta p ORDER BY p.creadoEn DESC")
    List<McasPropuesta> findTodasOrdenadas();

    /**
     * Recuento de propuestas por estado (contador de pendientes del menú). Cada
     * fila es {@code [String estado, Long total]}; solo salen los estados pedidos
     * que tengan alguna propuesta.
     */
    @Query("SELECT p.estado, COUNT(p) FROM McasPropuesta p WHERE p.estado IN :estados GROUP BY p.estado")
    List<Object[]> contarPorEstado(@Param("estados") Collection<String> estados);

    /** Si alguna propuesta se calculó con el conjunto (entonces no puede volver a borrador). */
    boolean existsByIdConjuntoReglas(Integer idConjuntoReglas);
}
