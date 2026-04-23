package ti4.contest.replay.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.entities.CombatReplayPromotionScoreBackfillEntity;

public interface CombatReplayPromotionScoreBackfillRepository
        extends JpaRepository<CombatReplayPromotionScoreBackfillEntity, String> {}
