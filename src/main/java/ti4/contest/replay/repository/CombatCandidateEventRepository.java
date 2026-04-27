package ti4.contest.replay.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ti4.contest.replay.core.CombatCandidateEventType;
import ti4.contest.replay.entities.CombatCandidateEventEntity;

public interface CombatCandidateEventRepository extends JpaRepository<CombatCandidateEventEntity, Long> {
    Optional<CombatCandidateEventEntity> findByCandidateIdAndSequenceNumber(Long candidateId, Integer sequenceNumber);

    boolean existsByCandidateIdAndEventType(Long candidateId, CombatCandidateEventType eventType);

    boolean existsByCandidateIdAndEventTypeAndActorFactionAndRoundNumber(
            Long candidateId, CombatCandidateEventType eventType, String actorFaction, Integer roundNumber);

    List<CombatCandidateEventEntity> findByCandidateIdOrderBySequenceNumberAsc(Long candidateId);

    @Query("select max(e.roundNumber) from CombatCandidateEventEntity e where e.candidateId = :candidateId")
    Optional<Integer> findMaxRoundNumberByCandidateId(@Param("candidateId") Long candidateId);

    @Query("select max(e.sequenceNumber) from CombatCandidateEventEntity e where e.candidateId = :candidateId")
    Optional<Integer> findMaxSequenceNumberByCandidateId(@Param("candidateId") Long candidateId);

    List<CombatCandidateEventEntity> findByOccurredAtBefore(LocalDateTime occurredAt);
}
