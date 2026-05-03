package ti4.contest.replay.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ti4.contest.replay.core.CombatCandidatePromotionStatus;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.contest.replay.entities.CombatCandidateEntity;

public interface CombatCandidateRepository extends JpaRepository<CombatCandidateEntity, Long> {
    CombatCandidateEntity findFirstByGameNameAndTilePositionAndStatus(
            String gameName, String tilePosition, CombatCandidateStatus status);

    List<CombatCandidateEntity> findByGameNameAndStatus(String gameName, CombatCandidateStatus status);

    List<CombatCandidateEntity> findByGameNameAndStatusIn(String gameName, Collection<CombatCandidateStatus> statuses);

    CombatCandidateEntity findFirstByGameNameAndTilePositionAndStatusIn(
            String gameName, String tilePosition, Collection<CombatCandidateStatus> statuses);

    List<CombatCandidateEntity> findByStatus(CombatCandidateStatus status);

    List<CombatCandidateEntity> findByStatusAndPendingResolutionStartedAtBefore(
            CombatCandidateStatus status, LocalDateTime pendingResolutionStartedAt);

    @Query("""
            select c from CombatCandidateEntity c
            where c.gameName = :gameName
              and c.status = :status
              and (lower(c.attackerFaction) = lower(:faction) or lower(c.defenderFaction) = lower(:faction))
            order by c.startedAt asc
            """)
    List<CombatCandidateEntity> findTrackingCandidatesForFaction(
            @Param("gameName") String gameName,
            @Param("status") CombatCandidateStatus status,
            @Param("faction") String faction);

    @Query("""
            select c from CombatCandidateEntity c
            where c.status = :status
              and c.promotionStatus = :promotionStatus
              and c.resolvedAt is not null
              and c.resolvedAt >= :resolvedAfter
            """)
    List<CombatCandidateEntity> findResolvedPromotionCandidates(
            @Param("status") CombatCandidateStatus status,
            @Param("promotionStatus") CombatCandidatePromotionStatus promotionStatus,
            @Param("resolvedAfter") LocalDateTime resolvedAfter);

    List<CombatCandidateEntity> findByPromotionStatusAndResolvedAtBefore(
            CombatCandidatePromotionStatus promotionStatus, LocalDateTime resolvedAt);
}
