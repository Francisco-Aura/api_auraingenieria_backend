package api.aura.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import api.aura.entity.TBProductos;

@Repository
public interface TBProductosRepository extends JpaRepository<TBProductos, Integer> {
}
