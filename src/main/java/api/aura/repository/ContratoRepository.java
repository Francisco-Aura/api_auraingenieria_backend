package api.aura.repository;

import api.aura.entity.Contrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContratoRepository extends JpaRepository<Contrato, Integer> {
    // Consultas personalizadas si son necesarias
    List<Contrato> findByClienteId(Integer clienteId);
    List<Contrato> findByPuntoSuministroId(Integer puntoSuministroId);
}
