package api.aura.repository;

import api.aura.entity.Contrato;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContratoRepository extends JpaRepository<Contrato, Integer> {
    /** Serializa los disparos MCAS del mismo contrato dentro de la transaccion. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Contrato c WHERE c.id = :id")
    Optional<Contrato> findByIdForUpdate(@Param("id") Integer id);

    // Consultas personalizadas si son necesarias
    List<Contrato> findByClienteId(Integer clienteId);

    /** Contrato por su número (el más reciente si se repitiera); lo usa la simulación MCAS. */
    Optional<Contrato> findFirstByContratoOrderByIdDesc(String contrato);

    /** Contratos con ese número exacto, los 10 más recientes primero (buscar contrato en MCAS). */
    List<Contrato> findTop10ByContratoOrderByIdDesc(String contrato);

    /**
     * Contratos cuyo número empieza por el texto, los 10 más recientes primero.
     * Spring Data escapa «%» y «_»: se buscan literalmente.
     */
    List<Contrato> findTop10ByContratoStartingWithOrderByIdDesc(String prefijo);
    List<Contrato> findByPuntoSuministroId(Integer puntoSuministroId);

    // Compatibilidad historica: NULL, ano 1 y ano 1900 representan "sin baja".
    @Query("SELECT c FROM Contrato c WHERE c.cliente.id = :clienteId "
         + "AND (c.fechaBaja IS NULL OR YEAR(c.fechaBaja) = 1 OR YEAR(c.fechaBaja) = 1900)")
    List<Contrato> findActivosPorClienteId(@Param("clienteId") Integer clienteId);
    
    @Query("SELECT c FROM Contrato c WHERE c.fechaFin > CURRENT_DATE "
         + "AND (c.fechaBaja IS NULL OR YEAR(c.fechaBaja) = 1 OR YEAR(c.fechaBaja) = 1900)")
    List<Contrato> findContratosActivos();

    // Sincroniza exención de impuesto eléctrico y autoconsumo de todos los contratos de un
    // punto de suministro en una sola sentencia, en vez de cargar cada Contrato completo
    // (con su Cliente y PuntoSuministro EAGER) solo para modificar estos dos campos.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Contrato c SET c.exencionImpuestoElectricidad = :exencionImpuestoElectricidad, " +
            "c.autoconsumo = :autoconsumo WHERE c.puntoSuministro.id = :puntoSuministroId")
    int actualizarExencionYAutoconsumoPorPuntoSuministro(
            @Param("puntoSuministroId") Integer puntoSuministroId,
            @Param("exencionImpuestoElectricidad") Integer exencionImpuestoElectricidad,
            @Param("autoconsumo") Integer autoconsumo);
}
