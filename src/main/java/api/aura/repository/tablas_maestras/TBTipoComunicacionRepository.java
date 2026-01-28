package api.aura.repository.tablas_maestras;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import api.aura.entity.tablas_maestras.TBTipoComunicacion;

@Repository
public interface TBTipoComunicacionRepository extends JpaRepository<TBTipoComunicacion, Integer> {
} 