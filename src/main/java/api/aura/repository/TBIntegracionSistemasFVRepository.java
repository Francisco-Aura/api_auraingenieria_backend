package api.aura.repository;

import api.aura.entity.IntegracionSistemasFV;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TBIntegracionSistemasFVRepository extends JpaRepository<TBIntegracionSistemasFV, Long> {
    
}
