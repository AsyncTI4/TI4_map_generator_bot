package ti4.contest.replay.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatCandidateEventType;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;

/**
 * Appends ordered events to a candidate while keeping the candidate sequence counter in sync.
 */
@Service
@RequiredArgsConstructor
class CombatReplayEventAppender {

    private final CombatCandidateRepository candidateRepository;
    private final CombatCandidateEventRepository candidateEventRepository;
    private final ReplayDispatchSerializer payloadSerializer;

    public synchronized void appendEvent(
            CombatCandidateEntity candidate,
            CombatCandidateEventType eventType,
            Integer roundNumber,
            String actorFaction,
            String summaryText,
            ReplayDispatchPayload payload) {
        CombatCandidateEntity freshCandidate =
                candidateRepository.findById(candidate.getId()).orElse(null);
        if (freshCandidate == null) return;
        CombatCandidateStatus status = freshCandidate.getStatus();
        boolean pendingHitAssignment =
                status == CombatCandidateStatus.PENDING_RESOLUTION && eventType == CombatCandidateEventType.HIT_ASSIGN;
        boolean terminalEvent =
                eventType == CombatCandidateEventType.RESOLVED || eventType == CombatCandidateEventType.CANCELLED;
        if (status != CombatCandidateStatus.TRACKING && !pendingHitAssignment && !terminalEvent) return;

        int sequenceNumber = nextSequenceNumber(freshCandidate);
        CombatCandidateEventEntity event = new CombatCandidateEventEntity();
        event.setCandidateId(freshCandidate.getId());
        event.setOccurredAt(LocalDateTime.now());
        event.setSequenceNumber(sequenceNumber);
        event.setEventType(eventType);
        event.setRoundNumber(roundNumber);
        event.setActorFaction(actorFaction);
        event.setSummaryText(summaryText);
        event.setPayloadJson(payloadSerializer.write(payload));
        candidateEventRepository.save(event);

        freshCandidate.setNextEventSequence(sequenceNumber + 1);
        candidateRepository.save(freshCandidate);
    }

    private int nextSequenceNumber(CombatCandidateEntity candidate) {
        int nextFromEvents = candidateEventRepository
                        .findMaxSequenceNumberByCandidateId(candidate.getId())
                        .orElse(0)
                + 1;
        return Math.max(candidate.getNextEventSequence(), nextFromEvents);
    }
}
