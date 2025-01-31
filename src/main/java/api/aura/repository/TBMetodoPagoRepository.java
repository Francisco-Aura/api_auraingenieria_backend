package api.aura.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import api.aura.entity.TBMetodoPago;

@Repository
public interface TBMetodoPagoRepository extends JpaRepository<TBMetodoPago, Integer> {
}
