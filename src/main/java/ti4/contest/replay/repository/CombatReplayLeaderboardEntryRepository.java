package ti4.contest.replay.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;

public interface CombatReplayLeaderboardEntryRepository
        extends JpaRepository<CombatReplayLeaderboardEntryEntity, Long> {
    List<CombatReplayLeaderboardEntryEntity> findByDiscordUserIdIn(Collection<String> discordUserIds);

    Optional<CombatReplayLeaderboardEntryEntity> findByDiscordUserId(String discordUserId);

    List<CombatReplayLeaderboardEntryEntity>
            findTop10ByOrderByTotalPointsDescCorrectPredictionsDescPredictionCountDescDiscordUserNameAsc();
}
