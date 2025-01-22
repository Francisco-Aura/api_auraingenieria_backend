package api.aura.repository;

import api.aura.entity.PuntoSuministro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PuntoSuministroRepository extends JpaRepository<PuntoSuministro, Integer> {
    // Consulta personalizada para obtener todos los puntos de suministro por cliente
    List<PuntoSuministro> findByClienteId(Integer clienteId);
}