package ti4.contest.replay.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatReplayHouseAbilityVoteEntity;

public interface CombatReplayHouseAbilityVoteRepository
        extends JpaRepository<CombatReplayHouseAbilityVoteEntity, Long> {

    List<CombatReplayHouseAbilityVoteEntity> findByCandidateIdAndHouse(Long candidateId, CombatReplayHouse house);

    Optional<CombatReplayHouseAbilityVoteEntity> findByCandidateIdAndHouseAndDiscordUserId(
            Long candidateId, CombatReplayHouse house, String discordUserId);

    void deleteByCandidateIdAndHouse(Long candidateId, CombatReplayHouse house);
}
