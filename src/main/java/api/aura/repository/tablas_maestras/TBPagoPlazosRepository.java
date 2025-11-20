package api.aura.repository.tablas_maestras;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import api.aura.entity.tablas_maestras.TBPagoPlazos;

import java.util.List;

@Repository
public interface TBPagoPlazosRepository extends JpaRepository<TBPagoPlazos, Integer> {
    
    List<TBPagoPlazos> findAllByOrderByNumeroCuotasAsc();
}
