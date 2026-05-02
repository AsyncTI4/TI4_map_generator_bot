package ti4.contest.replay.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.entities.CombatReplayHacanSubsidyVoteEntity;

public interface CombatReplayHacanSubsidyVoteRepository
        extends JpaRepository<CombatReplayHacanSubsidyVoteEntity, Long> {

    List<CombatReplayHacanSubsidyVoteEntity> findByContestId(Long contestId);

    List<CombatReplayHacanSubsidyVoteEntity> findByContestIdAndDiscordUserId(Long contestId, String discordUserId);

    Optional<CombatReplayHacanSubsidyVoteEntity> findByContestIdAndDiscordUserIdAndBetTypeAndTargetFaction(
            Long contestId, String discordUserId, CombatSideBetType betType, String targetFaction);

    void deleteByContestId(Long contestId);
}
