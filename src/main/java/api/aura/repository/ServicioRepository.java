package api.aura.repository;

import api.aura.entity.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServicioRepository extends JpaRepository<Servicio, Integer> {
    List<Servicio> findByClienteId(Integer clienteId);
    List<Servicio> findByPuntoSuministroId(Integer puntoSuministroId);
}
