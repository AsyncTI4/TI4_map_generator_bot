package ti4.contest.replay.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.entities.CombatReplayHacanMarketCompactDecisionEntity;

public interface CombatReplayHacanMarketCompactDecisionRepository
        extends JpaRepository<CombatReplayHacanMarketCompactDecisionEntity, Long> {

    Optional<CombatReplayHacanMarketCompactDecisionEntity> findByContestId(Long contestId);

    void deleteByContestId(Long contestId);
}
