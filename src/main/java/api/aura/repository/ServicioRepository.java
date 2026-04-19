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

    // Baja en cascada: servicios activos del cliente
    List<Servicio> findByClienteIdAndFechaBajaIsNull(Integer clienteId);

    @Query("SELECT DISTINCT s.puntoSuministro.id FROM Servicio s "
         + "WHERE s.idProducto IN :idProductos")
    Set<Integer> findIdsPuntosSuministroConServicioActivo(@Param("idProductos") List<Integer> idProductos);
}
