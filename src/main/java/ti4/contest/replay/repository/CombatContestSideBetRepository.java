package ti4.contest.replay.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.entities.CombatContestSideBetEntity;

public interface CombatContestSideBetRepository extends JpaRepository<CombatContestSideBetEntity, Long> {

    int countByContestIdAndDiscordUserId(Long contestId, String discordUserId);

    List<CombatContestSideBetEntity> findByContestId(Long contestId);
}
