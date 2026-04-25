package ti4.contest.replay.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ti4.contest.replay.core.CombatContestReplayStatus;
import ti4.contest.replay.entities.CombatReplayContestEntity;

public interface CombatReplayContestRepository extends JpaRepository<CombatReplayContestEntity, Long> {
    Optional<CombatReplayContestEntity> findByCandidateId(Long candidateId);

    List<CombatReplayContestEntity> findByReplayStatusInAndNextReplayAtLessThanEqualOrderByNextReplayAtAsc(
            Collection<CombatContestReplayStatus> replayStatuses, LocalDateTime nextReplayAt);

    boolean existsByPostedAtGreaterThanEqual(LocalDateTime postedAt);

    long countByPostedAtGreaterThanEqual(LocalDateTime postedAt);

    List<CombatReplayContestEntity>
            findTop5ByReplayStatusAndReplayCompletedAtIsNotNullAndLeaderboardPostedAtIsNullOrderByReplayCompletedAtAsc(
                    CombatContestReplayStatus replayStatus);
}
