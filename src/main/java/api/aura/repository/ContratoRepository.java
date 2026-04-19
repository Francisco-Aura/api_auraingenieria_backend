package api.aura.repository;

import api.aura.entity.Contrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContratoRepository extends JpaRepository<Contrato, Integer> {
    // Consultas personalizadas si son necesarias
    List<Contrato> findByClienteId(Integer clienteId);
    List<Contrato> findByPuntoSuministroId(Integer puntoSuministroId);

    // Baja en cascada: contratos activos del cliente
    List<Contrato> findByClienteIdAndFechaBajaIsNull(Integer clienteId);
    
    @Query("SELECT c FROM Contrato c WHERE c.fechaFin > CURRENT_DATE AND (c.fechaBaja IS NULL OR c.fechaBaja = DATE('1900-01-01'))")
    List<Contrato> findContratosActivos();
}
