package api.aura.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import api.aura.entity.TBSectores;

@Repository
public interface TBSectoresRepository extends JpaRepository<TBSectores, Integer> {
}
