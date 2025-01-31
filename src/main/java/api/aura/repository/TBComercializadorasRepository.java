package api.aura.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import api.aura.entity.TBComercializadoras;

@Repository
public interface TBComercializadorasRepository extends JpaRepository<TBComercializadoras, Integer> {
}