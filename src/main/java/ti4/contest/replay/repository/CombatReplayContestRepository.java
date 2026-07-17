package ti4.contest.replay.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import ti4.contest.replay.core.CombatContestReplayStatus;
import ti4.contest.replay.entities.CombatReplayContestEntity;

public interface CombatReplayContestRepository extends JpaRepository<CombatReplayContestEntity, Long> {
    Optional<CombatReplayContestEntity> findByCandidateId(Long candidateId);

    Optional<CombatReplayContestEntity> findByPublicMessageId(Long publicMessageId);

    Optional<CombatReplayContestEntity> findByPublicMessageIdOrPublicThreadId(
            Long publicMessageId, Long publicThreadId);

    Optional<CombatReplayContestEntity> findFirstByIdLessThanOrderByIdDesc(Long id);

    Optional<CombatReplayContestEntity> findFirstByOrderByIdDesc();

    List<CombatReplayContestEntity> findAllByOrderByIdDesc();

    boolean existsByIdGreaterThan(Long id);

    boolean existsByIdGreaterThanAndReplayCompletedAtIsNotNull(Long id);

    List<CombatReplayContestEntity> findByReplayStatusInAndNextReplayAtLessThanEqualOrderByNextReplayAtAsc(
            Collection<CombatContestReplayStatus> replayStatuses, LocalDateTime nextReplayAt);

    List<CombatReplayContestEntity>
            findByReplayStatusAndReplayStartWarningPostedAtIsNullAndReplayStartAtBetweenOrderByReplayStartAtAsc(
                    CombatContestReplayStatus replayStatus, LocalDateTime startInclusive, LocalDateTime endInclusive);

    @Transactional
    @Modifying
    @Query("""
            update CombatReplayContestEntity contest
            set contest.replayStartWarningPostedAt = ?2
            where contest.id = ?1
                and contest.replayStartWarningPostedAt is null
            """)
    int markReplayStartWarningPostedIfUnset(Long contestId, LocalDateTime postedAt);

    boolean existsByReplayStatusIn(Collection<CombatContestReplayStatus> replayStatuses);

    boolean existsByPostedAtGreaterThanEqual(LocalDateTime postedAt);

    long countByPostedAtGreaterThanEqual(LocalDateTime postedAt);

    List<CombatReplayContestEntity>
            findTop5ByReplayStatusAndReplayCompletedAtIsNotNullAndLeaderboardPostedAtIsNullOrderByReplayCompletedAtAsc(
                    CombatContestReplayStatus replayStatus);
}
