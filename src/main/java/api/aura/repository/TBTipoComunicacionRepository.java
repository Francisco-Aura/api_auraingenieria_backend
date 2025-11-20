package api.aura.repository;

import api.aura.entity.TBTipoComunicacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TBTipoComunicacionRepository extends JpaRepository<TBTipoComunicacion, Integer> {
} 