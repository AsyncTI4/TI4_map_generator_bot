package ti4.contest.replay.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatReplayHouseScoreEntity;

public interface CombatReplayHouseScoreRepository extends JpaRepository<CombatReplayHouseScoreEntity, Long> {
    List<CombatReplayHouseScoreEntity> findByContestId(Long contestId);

    List<CombatReplayHouseScoreEntity> findByHouse(CombatReplayHouse house);
}
