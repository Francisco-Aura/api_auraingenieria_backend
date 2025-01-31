package api.aura.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import api.aura.entity.TBTarifasGas;

@Repository
public interface TBTarifasGasRepository extends JpaRepository<TBTarifasGas, Integer> {
}
