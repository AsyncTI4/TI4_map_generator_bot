package ti4.contest.replay.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.entities.CombatReplayHacanTradeConvoysVoteEntity;

public interface CombatReplayHacanTradeConvoysVoteRepository
        extends JpaRepository<CombatReplayHacanTradeConvoysVoteEntity, Long> {

    List<CombatReplayHacanTradeConvoysVoteEntity> findByContestId(Long contestId);

    Optional<CombatReplayHacanTradeConvoysVoteEntity> findByContestIdAndDiscordUserId(
            Long contestId, String discordUserId);

    void deleteByContestId(Long contestId);
}
