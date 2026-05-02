package ti4.contest.replay.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatReplayHouseEntity;

public interface CombatReplayHouseRepository extends JpaRepository<CombatReplayHouseEntity, Long> {
    Optional<CombatReplayHouseEntity> findByDiscordUserId(String discordUserId);

    List<CombatReplayHouseEntity> findByDiscordUserIdIn(Collection<String> discordUserIds);

    long countByHouse(CombatReplayHouse house);
}
