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
                    ps.CUPSgas
                )
                FROM PuntoSuministro ps
                LEFT JOIN Cliente c ON ps.cliente.id = c.id
        """)
    List<PuntosSuministrosCarteraDTO> findAllPuntosSuministro();
}
