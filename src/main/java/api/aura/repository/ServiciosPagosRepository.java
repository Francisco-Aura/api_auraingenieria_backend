package api.aura.repository;

import api.aura.entity.ServiciosPagos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiciosPagosRepository extends JpaRepository<ServiciosPagos, Integer> {
    Optional<ServiciosPagos> findByServicioId(Integer idServicio);

    List<ServiciosPagos> findByServicioIdIn(Collection<Integer> idsServicio);
}