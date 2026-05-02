package ti4.contest.replay.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.entities.CombatReplayHacanTradeConvoysEntity;

public interface CombatReplayHacanTradeConvoysRepository
        extends JpaRepository<CombatReplayHacanTradeConvoysEntity, Long> {

    Optional<CombatReplayHacanTradeConvoysEntity> findByContestId(Long contestId);

    void deleteByContestId(Long contestId);
}
