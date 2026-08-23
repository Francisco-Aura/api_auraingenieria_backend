package api.aura.repository;

import api.aura.entity.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ServicioRepository extends JpaRepository<Servicio, Integer> {
    List<Servicio> findByClienteId(Integer clienteId);
    List<Servicio> findByPuntoSuministroId(Integer puntoSuministroId);

    @Query("SELECT s FROM Servicio s LEFT JOIN FETCH s.cliente WHERE s.puntoSuministro.id = :id")
    List<Servicio> findByPuntoSuministroIdWithCliente(@Param("id") Integer id);

    // Compatibilidad historica: NULL, ano 1 y ano 1900 representan "sin baja".
    @Query("SELECT s FROM Servicio s WHERE s.cliente.id = :clienteId "
         + "AND (s.fechaBaja IS NULL OR YEAR(s.fechaBaja) = 1 OR YEAR(s.fechaBaja) = 1900)")
    List<Servicio> findActivosPorClienteId(@Param("clienteId") Integer clienteId);

    @Query("SELECT DISTINCT s.puntoSuministro.id FROM Servicio s "
         + "WHERE s.idProducto IN :idProductos "
         + "AND s.fechaInicioServicio <= CURRENT_DATE AND s.fechaFinServicio > CURRENT_DATE "
         + "AND (s.fechaBaja IS NULL OR YEAR(s.fechaBaja) = 1 OR YEAR(s.fechaBaja) = 1900)")
    Set<Integer> findIdsPuntosSuministroConServicioActivo(@Param("idProductos") List<Integer> idProductos);

    // Cuenta servicios vigentes hoy (misma regla que esServicioVigente en el frontend:
    // fechaInicioServicio <= hoy && fechaFinServicio > hoy). Evita cargar todas las filas.
    @Query("SELECT COUNT(s) FROM Servicio s "
         + "WHERE s.fechaInicioServicio <= CURRENT_DATE AND s.fechaFinServicio > CURRENT_DATE "
         + "AND (s.fechaBaja IS NULL OR YEAR(s.fechaBaja) = 1 OR YEAR(s.fechaBaja) = 1900)")
    long countServiciosActivos();
}
