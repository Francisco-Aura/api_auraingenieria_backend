package api.aura.repository.mcas;

import api.aura.entity.mcas.McasValorRango;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface McasValorRangoRepository extends JpaRepository<McasValorRango, Integer> {
    List<McasValorRango> findByIdReglaOrderByOrdenAsc(Integer idRegla);
    long deleteByIdRegla(Integer idRegla);

    /**
     * Dimensiones distintas de cada regla, en una sola consulta (datos que piden
     * las tablas de comisiones). Ordenadas por regla y dimensión.
     */
    @Query("SELECT DISTINCT v.idRegla AS idRegla, v.dimension AS dimension FROM McasValorRango v "
         + "WHERE v.idRegla IN :ids ORDER BY v.idRegla, v.dimension")
    List<DimensionRegla> dimensionesPorReglas(@Param("ids") Collection<Integer> idsRegla);

    /** Par regla–dimensión de {@link #dimensionesPorReglas}. */
    interface DimensionRegla {
        Integer getIdRegla();
        String getDimension();
    }
}
