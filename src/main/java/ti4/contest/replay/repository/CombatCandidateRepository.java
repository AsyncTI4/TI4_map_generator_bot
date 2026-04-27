package ti4.contest.replay.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ti4.contest.replay.core.CombatCandidatePromotionStatus;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.contest.replay.core.CombatContestType;
import ti4.contest.replay.entities.CombatCandidateEntity;

public interface CombatCandidateRepository extends JpaRepository<CombatCandidateEntity, Long> {
    CombatCandidateEntity findFirstByGameNameAndTilePositionAndCombatTypeAndStatus(
            String gameName, String tilePosition, CombatContestType combatType, CombatCandidateStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CombatCandidateEntity c where c.id = :id")
    Optional<CombatCandidateEntity> findByIdForUpdate(@Param("id") Long id);

    List<CombatCandidateEntity> findByGameNameAndStatus(String gameName, CombatCandidateStatus status);

    List<CombatCandidateEntity> findByStatus(CombatCandidateStatus status);

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
