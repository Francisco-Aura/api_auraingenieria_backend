package api.aura.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import api.aura.entity.TBNivelAcceso;

@Repository
public interface TBNivelAccesoRepository extends JpaRepository<TBNivelAcceso, Integer> {
}
