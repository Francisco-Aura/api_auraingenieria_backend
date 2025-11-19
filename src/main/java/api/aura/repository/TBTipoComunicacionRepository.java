package api.aura.repository;

import api.aura.entity.TipoComunicacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TBTipoComunicacionRepository extends JpaRepository<TBTipoComunicacion, Integer> {
} 