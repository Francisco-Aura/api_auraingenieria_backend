package api.aura.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import api.aura.entity.TBTarifasLuz;

@Repository
public interface TBTarifasLuzRepository extends JpaRepository<TBTarifasLuz, Integer> {
}
