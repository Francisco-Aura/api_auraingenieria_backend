package api.aura.repository;

import api.aura.entity.Propiedad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TBPropiedadRepository extends JpaRepository<TBPropiedad, Integer> {
} 