package api.aura.repository.tablas_maestras;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import api.aura.entity.tablas_maestras.TBProductos;

import java.util.Optional;

@Repository
public interface TBProductosRepository extends JpaRepository<TBProductos, Integer> {
    /** Producto de servicio por nombre aproximado (p. ej. "SWITCHING" para las comisiones MCAS). */
    Optional<TBProductos> findFirstByNombreContainingIgnoreCaseOrderByIdAsc(String nombre);
}
