package ti4.contest.replay.service;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatCandidateEventType;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.contest.replay.core.CombatReplayEventPayload;
import ti4.contest.replay.core.CombatReplayEventPayloadSerde;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;

@Service
@RequiredArgsConstructor
public class CombatReplayEventAppender {

    private final CombatCandidateRepository candidateRepository;
    private final CombatCandidateEventRepository candidateEventRepository;
    private final CombatReplayEventPayloadSerde payloadSerde;

    @Transactional
    public void appendEvent(
            CombatCandidateEntity candidate,
            CombatCandidateEventType eventType,
            Integer roundNumber,
            String actorFaction,
            String summaryText,
            CombatReplayEventPayload payload) {
        CombatCandidateEntity freshCandidate =
                candidateRepository.findByIdForUpdate(candidate.getId()).orElse(null);
        if (freshCandidate == null
                || (freshCandidate.getStatus() != CombatCandidateStatus.TRACKING
                        && eventType != CombatCandidateEventType.RESOLVED
                        && eventType != CombatCandidateEventType.CANCELLED)) {
            return;
        }

        CombatCandidateEventEntity event = new CombatCandidateEventEntity();
        event.setCandidateId(freshCandidate.getId());
        event.setOccurredAt(LocalDateTime.now());
        event.setSequenceNumber(freshCandidate.getNextEventSequence());
        event.setEventType(eventType);
        event.setRoundNumber(roundNumber);
        event.setActorFaction(actorFaction);
        event.setSummaryText(summaryText);
        event.setPayloadJson(payloadSerde.write(payload));
        candidateEventRepository.save(event);

        freshCandidate.setNextEventSequence(freshCandidate.getNextEventSequence() + 1);
        candidateRepository.save(freshCandidate);
    }
}
