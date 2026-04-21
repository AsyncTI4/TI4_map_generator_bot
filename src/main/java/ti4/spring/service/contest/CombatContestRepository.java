package ti4.spring.service.contest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CombatContestRepository extends JpaRepository<CombatContestEntity, Long> {

    Optional<CombatContestEntity> findFirstByOrderByPostedAtDesc();

    List<CombatContestEntity> findByGameNameAndStatusIn(String gameName, Collection<CombatContestStatus> statuses);

    List<CombatContestEntity> findTop5ByStatusAndResolvedAtIsNotNullAndLeaderboardPostedAtIsNullOrderByResolvedAtAsc(
            CombatContestStatus status);

    Optional<CombatContestEntity> findFirstByGameNameAndTilePositionAndCombatTypeAndStatusInOrderByPostedAtDesc(
            String gameName,
            String tilePosition,
            CombatContestType combatType,
            Collection<CombatContestStatus> statuses);
}
