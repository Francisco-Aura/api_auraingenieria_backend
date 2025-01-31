package api.aura.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import api.aura.entity.TBPagoPlazos;

@Repository
public interface TBPagoPlazosRepository extends JpaRepository<TBPagoPlazos, Integer> {
}
