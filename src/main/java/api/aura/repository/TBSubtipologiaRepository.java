package api.aura.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import api.aura.entity.TBSubtipologia;

@Repository
public interface TBSubtipologiaRepository extends JpaRepository<TBSubtipologia, Integer> {
}