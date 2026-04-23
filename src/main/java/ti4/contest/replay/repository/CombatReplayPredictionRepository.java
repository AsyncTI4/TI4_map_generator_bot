package ti4.contest.replay.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.entities.CombatReplayPredictionEntity;

public interface CombatReplayPredictionRepository extends JpaRepository<CombatReplayPredictionEntity, Long> {
    Optional<CombatReplayPredictionEntity> findByContestId(Long contestId);

    void deleteByContestId(Long contestId);
}
