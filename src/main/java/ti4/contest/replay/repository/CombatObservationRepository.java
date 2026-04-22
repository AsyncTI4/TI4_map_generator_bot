package ti4.contest.replay.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.entities.CombatObservationEntity;

public interface CombatObservationRepository extends JpaRepository<CombatObservationEntity, Long> {
    List<CombatObservationEntity> findByStartedAtGreaterThanEqualOrderByStartedAtAsc(LocalDateTime startedAt);

    List<CombatObservationEntity> findByStartedAtBefore(LocalDateTime startedAt);
}
