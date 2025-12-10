package api.aura.repository.tablas_maestras;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import api.aura.entity.tablas_maestras.TBNivelAcceso;

@Repository
public interface TBNivelAccesoRepository extends JpaRepository<TBNivelAcceso, Integer> {
}
