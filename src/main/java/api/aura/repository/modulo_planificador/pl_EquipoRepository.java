package api.aura.repository.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Equipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface pl_EquipoRepository extends JpaRepository<pl_Equipo, String> {

    List<pl_Equipo> findAllByOrderByPosicionAsc();

    long countByMarca(String marca);

    @Query("select max(e.posicion) from pl_Equipo e")
    Integer maxPosicion();

    @Query("select max(e.precioRevisadoEl) from pl_Equipo e")
    LocalDate ultimaRevision();

    long countByPrecioRevisadoElIsNotNull();
}
