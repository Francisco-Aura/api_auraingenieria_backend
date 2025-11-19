package api.aura.repository;

import api.aura.entity.Distribuidora;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TBDistribuidoraRepository extends JpaRepository<TBDistribuidora, Integer> {
} 