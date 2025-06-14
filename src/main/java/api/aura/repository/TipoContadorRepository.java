package api.aura.repository;

import api.aura.entity.TipoContador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TipoContadorRepository extends JpaRepository<TipoContador, Integer> {
} 