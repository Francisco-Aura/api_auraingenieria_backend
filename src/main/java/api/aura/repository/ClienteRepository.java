package api.aura.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import api.aura.dto.ClienteCarteraDTO;
import api.aura.entity.Cliente;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    @Query(value = "SELECT c.id AS idCliente, " +
                   "c.nombre, " +
                   "c.cif, " +
                   "CONCAT(c.direccion, ', ', c.poblacion, ', ', c.provincia) AS direccionFiscal, " +
                   "COUNT(ps.id) AS numeroPuntosSuministro, " +
                   "u.nombre AS usuarioAsignado " +
                   "FROM Clientes c " +
                   "LEFT JOIN PuntoSuministro ps ON c.id = ps.idCliente " +
                   "LEFT JOIN Usuarios u ON c.IdUsuario = u.id " +
                   "GROUP BY c.id, c.nombre, c.cif, c.direccion, c.poblacion, c.provincia, u.nombre",
           nativeQuery = true)
    List<ClienteCarteraDTO> obtenerCarteraClientes();
}