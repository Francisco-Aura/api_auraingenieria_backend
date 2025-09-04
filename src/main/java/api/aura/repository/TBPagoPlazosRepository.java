package api.aura.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import api.aura.entity.TBPagoPlazos;

import java.util.List;

@Repository
public interface TBPagoPlazosRepository extends JpaRepository<TBPagoPlazos, Integer> {
    
    List<TBPagoPlazos> findAllByOrderByNumeroCuotasAsc();
}
