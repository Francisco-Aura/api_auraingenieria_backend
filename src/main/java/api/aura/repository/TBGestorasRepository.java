package api.aura.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import api.aura.entity.TBGestoras;

@Repository
public interface TBGestorasRepository extends JpaRepository<TBGestoras, Integer> {
}
