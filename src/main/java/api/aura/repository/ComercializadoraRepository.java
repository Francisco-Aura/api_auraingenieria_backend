package api.aura.repository;

import api.aura.entity.Comercializadora;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComercializadoraRepository extends JpaRepository<Comercializadora, Long> {
    List<Comercializadora> findByActivoTrue();
} 