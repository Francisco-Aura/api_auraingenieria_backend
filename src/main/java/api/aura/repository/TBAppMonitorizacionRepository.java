package api.aura.repository;

import api.aura.entity.AppMonitorizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TBAppMonitorizacionRepository extends JpaRepository<TBAppMonitorizacion, Long> {
    
}
