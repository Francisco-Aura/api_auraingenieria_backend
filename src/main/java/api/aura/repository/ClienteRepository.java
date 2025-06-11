package api.aura.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import api.aura.dto.ClienteCarteraDTO;
import api.aura.entity.Cliente;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    @Query("""
        SELECT new api.aura.dto.ClienteCarteraDTO(
            c.id,
            c.nombre,
            c.cif,
            CONCAT(c.direccion, ', ', c.poblacion, ', ', c.provincia),
            COUNT(ps.id),
            u.nombre,
            c.nombreComercial
        )
        FROM Cliente c
        LEFT JOIN c.puntosSuministro ps
        LEFT JOIN c.usuario u
        GROUP BY c.id, c.nombre, c.cif, c.direccion, c.poblacion, c.provincia, u.nombre, c.nombreComercial
    """)
    List<ClienteCarteraDTO> obtenerCarteraClientes();
}
