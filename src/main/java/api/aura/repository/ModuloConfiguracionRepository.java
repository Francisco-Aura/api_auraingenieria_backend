package api.aura.repository;

import api.aura.entity.ModuloConfiguracion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface ModuloConfiguracionRepository extends JpaRepository<ModuloConfiguracion, Long> {
    Optional<ModuloConfiguracion> findByCodigo(String codigo);
    List<ModuloConfiguracion> findByCodigoStartingWithOrderByCodigoAsc(String prefijo);
}
