package ti4.contest.replay.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.entities.CombatDoubleOrBustEntity;

public interface CombatDoubleOrBustRepository extends JpaRepository<CombatDoubleOrBustEntity, Long> {

    Optional<CombatDoubleOrBustEntity> findByContestIdAndDiscordUserId(Long contestId, String discordUserId);

    List<CombatDoubleOrBustEntity> findByContestIdAndEnabledTrue(Long contestId);

    List<CombatDoubleOrBustEntity> findByContestIdAndDiscordUserIdIn(Long contestId, Collection<String> discordUserIds);
}
