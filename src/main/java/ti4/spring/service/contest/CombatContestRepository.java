package ti4.spring.service.contest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CombatContestRepository extends JpaRepository<CombatContestEntity, Long> {

    @Query(value = """
            select *
            from combat_predictor_contest
            where dice_rolled is null or dice_rolled = true
            order by posted_at desc
            limit 1
            """, nativeQuery = true)
    Optional<CombatContestEntity> findLatestContestCountingTowardCooldown();

    List<CombatContestEntity> findByGameNameAndStatusIn(String gameName, Collection<CombatContestStatus> statuses);

    List<CombatContestEntity> findTop5ByStatusAndResolvedAtIsNotNullAndLeaderboardPostedAtIsNullOrderByResolvedAtAsc(
            CombatContestStatus status);

    Optional<CombatContestEntity> findFirstByGameNameAndTilePositionAndCombatTypeAndStatusInOrderByPostedAtDesc(
            String gameName,
            String tilePosition,
            CombatContestType combatType,
            Collection<CombatContestStatus> statuses);
}
