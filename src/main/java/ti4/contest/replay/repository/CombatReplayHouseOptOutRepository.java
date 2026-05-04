package ti4.contest.replay.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.entities.CombatReplayHouseOptOutEntity;

public interface CombatReplayHouseOptOutRepository extends JpaRepository<CombatReplayHouseOptOutEntity, Long> {
    Optional<CombatReplayHouseOptOutEntity> findByDiscordUserId(String discordUserId);
}
