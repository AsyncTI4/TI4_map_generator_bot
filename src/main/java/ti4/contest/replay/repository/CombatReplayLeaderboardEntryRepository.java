package ti4.contest.replay.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;

public interface CombatReplayLeaderboardEntryRepository
        extends JpaRepository<CombatReplayLeaderboardEntryEntity, Long> {
    List<CombatReplayLeaderboardEntryEntity> findByDiscordUserIdIn(Collection<String> discordUserIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CombatReplayLeaderboardEntryEntity e where e.discordUserId = :discordUserId")
    Optional<CombatReplayLeaderboardEntryEntity> findByDiscordUserIdForUpdate(
            @Param("discordUserId") String discordUserId);

    List<CombatReplayLeaderboardEntryEntity>
            findTop10ByOrderByTotalPointsDescCorrectPredictionsDescPredictionCountDescDiscordUserNameAsc();
}
