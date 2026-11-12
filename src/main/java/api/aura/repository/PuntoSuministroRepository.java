package api.aura.repository;

import api.aura.dto.PuntosSuministrosCarteraDTO;
import api.aura.entity.PuntoSuministro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PuntoSuministroRepository extends JpaRepository<PuntoSuministro, Integer> {
    // Obtiene los puntos de suministro asociados a un cliente específico
    List<PuntoSuministro> findByClienteId(Integer clienteId);

    // Compatibilidad historica: NULL, ano 1 y ano 1900 representan "sin baja".
    @Query("SELECT ps FROM PuntoSuministro ps WHERE ps.cliente.id = :clienteId "
         + "AND (ps.fechaBaja IS NULL OR YEAR(ps.fechaBaja) = 1 OR YEAR(ps.fechaBaja) = 1900)")
    List<PuntoSuministro> findActivosPorClienteId(@Param("clienteId") Integer clienteId);

    // Reactivación selectiva: puntos con fecha de baja del cliente. Cualquier fecha cuenta
    // (también 1900), igual que la pantalla de Puntos de suministro, que los muestra como
    // dados de baja: así al reactivar se ofrecen y se les quita la fecha, como siempre
    List<PuntoSuministro> findByClienteIdAndFechaBajaIsNotNull(Integer clienteId);

    /**
     * Consulta para obtener una lista de puntos de suministro en formato DTO.
     * Incluye la tarifa del contrato actual (vigente) si existe y las potencias
     * contratadas.
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
                ps.identificacion,
                ps.CUPSluz,
                ps.CUPSgas,
                        ps.fechaBaja,
                CASE
                    WHEN ps.CUPSluz IS NOT NULL AND ps.CUPSluz != '' AND tl.nombre IS NOT NULL AND tl.nombre != 'Sin Tarifa' THEN tl.nombre
                    WHEN ps.CUPSgas IS NOT NULL AND ps.CUPSgas != '' AND tg.nombre IS NOT NULL AND tg.nombre != 'Sin Tarifa' THEN tg.nombre
                    ELSE NULL
                        END,
                        com.nombre,
                        (
                            SELECT MAX(con3.fechaFin)
                            FROM Contrato con3
                            WHERE con3.puntoSuministro.id = ps.id
                        ),
                con.potencia1,
                con.potencia2,
                con.potencia3,
                con.potencia4,
                con.potencia5,
                con.potencia6
                    )
                    FROM PuntoSuministro ps
                    LEFT JOIN Cliente c ON ps.cliente.id = c.id
                    LEFT JOIN Contrato con ON ps.id = con.puntoSuministro.id
                        AND con.fechaFin > CURRENT_DATE
                        AND con.id = (
                            SELECT MAX(con2.id)
                            FROM Contrato con2
                            WHERE con2.puntoSuministro.id = ps.id
                            AND con2.fechaFin > CURRENT_DATE
                        )
                    LEFT JOIN TBTarifasLuz tl ON con.idTarifaLuz = tl.id
                    LEFT JOIN TBTarifasGas tg ON con.idTarifaGas = tg.id
                    LEFT JOIN TBComercializadoras com ON con.idComercializadoraActual = com.id
                    WHERE c.id IS NOT NULL
            """)
    List<PuntosSuministrosCarteraDTO> findAllPuntosSuministro();

    /**
     * Consulta para obtener puntos de suministro de un cliente específico en
     * formato DTO.
     * Incluye la tarifa del contrato actual (vigente) si existe y las potencias
     * contratadas.
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
                        ps.identificacion,
                        ps.CUPSluz,
                        ps.CUPSgas,
                        ps.fechaBaja,
                        CASE
                            WHEN ps.CUPSluz IS NOT NULL AND ps.CUPSluz != '' AND tl.nombre IS NOT NULL AND tl.nombre != 'Sin Tarifa' THEN tl.nombre
                            WHEN ps.CUPSgas IS NOT NULL AND ps.CUPSgas != '' AND tg.nombre IS NOT NULL AND tg.nombre != 'Sin Tarifa' THEN tg.nombre
                            ELSE NULL
                        END,
                        com.nombre,
                        (
                            SELECT MAX(con3.fechaFin)
                            FROM Contrato con3
                            WHERE con3.puntoSuministro.id = ps.id
                        ),
                        con.potencia1,
                        con.potencia2,
                        con.potencia3,
                        con.potencia4,
                        con.potencia5,
                        con.potencia6
                    )
                    FROM PuntoSuministro ps
                    LEFT JOIN Cliente c ON ps.cliente.id = c.id
                    LEFT JOIN Contrato con ON ps.id = con.puntoSuministro.id
                        AND con.fechaFin > CURRENT_DATE
                        AND con.id = (
                            SELECT MAX(con2.id)
                            FROM Contrato con2
                            WHERE con2.puntoSuministro.id = ps.id
                            AND con2.fechaFin > CURRENT_DATE
                        )
                    LEFT JOIN TBTarifasLuz tl ON con.idTarifaLuz = tl.id
                    LEFT JOIN TBTarifasGas tg ON con.idTarifaGas = tg.id
                    LEFT JOIN TBComercializadoras com ON con.idComercializadoraActual = com.id
                    WHERE c.id = :clienteId
            """)
    List<PuntosSuministrosCarteraDTO> findAllPuntosSuministroPorCliente(@Param("clienteId") Integer clienteId);
}
