package api.aura.repository.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_HistorialPrecio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface pl_HistorialPrecioRepository extends JpaRepository<pl_HistorialPrecio, String> {

    List<pl_HistorialPrecio> findByEquipoIdOrderByFechaAsc(String equipoId);

    void deleteByEquipoId(String equipoId);
}
