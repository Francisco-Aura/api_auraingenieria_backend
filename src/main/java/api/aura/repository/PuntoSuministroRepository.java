package api.aura.repository;

import api.aura.dto.PuntosSuministrosCarteraDTO;
import api.aura.entity.PuntoSuministro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PuntoSuministroRepository extends JpaRepository<PuntoSuministro, Integer> {
    // Obtiene los puntos de suministro asociados a un cliente específico
    List<PuntoSuministro> findByClienteId(Integer clienteId);

    /**
     * Consulta para obtener una lista de puntos de suministro en formato DTO.
     * Incluye la tarifa del contrato actual (vigente) si existe.
     */
    @Query("""
                    SELECT new api.aura.dto.PuntosSuministrosCarteraDTO(
                        ps.id,
                        c.id,
                        c.nombre,
                        ps.direccion,
                        ps.codigoPostal,
                        ps.poblacion,
                        ps.provincia,
                        ps.CUPSluz,
                        ps.CUPSgas,
                        CASE
                            WHEN ps.CUPSluz IS NOT NULL AND ps.CUPSluz != '' AND tl.nombre IS NOT NULL AND tl.nombre != 'Sin Tarifa' THEN tl.nombre
                            WHEN ps.CUPSgas IS NOT NULL AND ps.CUPSgas != '' AND tg.nombre IS NOT NULL AND tg.nombre != 'Sin Tarifa' THEN tg.nombre
                            ELSE NULL
                        END
                    )
                    FROM PuntoSuministro ps
                    LEFT JOIN Cliente c ON ps.cliente.id = c.id
                    LEFT JOIN Contrato con ON ps.id = con.puntoSuministro.id
                        AND con.fechaFin > CURRENT_DATE
                        AND (con.fechaBaja IS NULL OR con.fechaBaja = DATE('1900-01-01'))
                    LEFT JOIN TBTarifasLuz tl ON con.idTarifaLuz = tl.id
                    LEFT JOIN TBTarifasGas tg ON con.idTarifaGas = tg.id
            """)
    List<PuntosSuministrosCarteraDTO> findAllPuntosSuministro();
}
