package ti4.contest.replay.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.*;
import ti4.contest.replay.entities.*;
import ti4.contest.replay.repository.*;

/**
 * Cleans up stale replay data and expires candidates that were never promoted in time.
 */
@Service
@RequiredArgsConstructor
public class CombatReplayJanitorService {

    private final CombatContestSettings settings;
    private final CombatCandidateRepository candidateRepository;
    private final CombatObservationRepository observationRepository;
    private final CombatCandidateEventRepository candidateEventRepository;
    private final CombatReplayContestRepository replayContestRepository;

    public void runJanitor() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime observationCutoff = now.minusDays(settings.getRetention().getObservationRetentionDays());

        expireStaleResolvedCandidates(now);
        observationRepository.deleteAll(observationRepository.findByStartedAtBefore(observationCutoff));
        deleteExpiredCandidateEvents(now);
        clearCompletedReplayErrors();
    }

    private void expireStaleResolvedCandidates(LocalDateTime now) {
        int expirationLookbackHours = Math.max(
                settings.getPromotion().getCandidateLookbackHours(),
                CombatContestSettings.PROMOTION_LOOKBACK_FALLBACK_MAX_HOURS);
        List<CombatCandidateEntity> staleCandidates = candidateRepository.findByPromotionStatusAndResolvedAtBefore(
                CombatCandidatePromotionStatus.PENDING, now.minusHours(expirationLookbackHours));
        for (CombatCandidateEntity candidate : staleCandidates) {
            if (candidate.getStatus() != CombatCandidateStatus.RESOLVED) continue;
            candidate.setPromotionStatus(CombatCandidatePromotionStatus.EXPIRED);
            candidateRepository.save(candidate);
        }
    }

    private void deleteExpiredCandidateEvents(LocalDateTime now) {
        LocalDateTime retentionCutoff = now.minusDays(settings.getRetention().getEventRetentionDays());
        List<CombatCandidateEventEntity> oldEvents = candidateEventRepository.findByOccurredAtBefore(retentionCutoff);
        if (oldEvents.isEmpty()) return;

        Set<Long> candidateIds = new HashSet<>();
        for (CombatCandidateEventEntity event : oldEvents) {
            candidateIds.add(event.getCandidateId());
        }

        Map<Long, CombatCandidateEntity> candidatesById = new HashMap<>();
        for (CombatCandidateEntity candidate : candidateRepository.findAllById(candidateIds)) {
            candidatesById.put(candidate.getId(), candidate);
        }

        Map<Long, CombatReplayContestEntity> contestsByCandidateId = new HashMap<>();
        for (Long candidateId : candidateIds) {
            replayContestRepository
                    .findByCandidateId(candidateId)
                    .ifPresent(contest -> contestsByCandidateId.put(contest.getCandidateId(), contest));
        }

        List<CombatCandidateEventEntity> toDelete = new ArrayList<>();
        for (CombatCandidateEventEntity event : oldEvents) {
            CombatCandidateEntity candidate = candidatesById.get(event.getCandidateId());
            if (candidate == null || candidate.getStatus() == CombatCandidateStatus.TRACKING) continue;

            if (!isPastRetentionCutoff(candidate, retentionCutoff)) {
                continue;
            }

            CombatReplayContestEntity contest = contestsByCandidateId.get(candidate.getId());
            if (contest != null && contest.getReplayStatus() != CombatContestReplayStatus.COMPLETED) continue;
            toDelete.add(event);
        }

        candidateEventRepository.deleteAll(toDelete);
    }

    private void clearCompletedReplayErrors() {
        for (CombatReplayContestEntity contest : replayContestRepository.findAll()) {
            if (contest.getReplayStatus() != CombatContestReplayStatus.COMPLETED) continue;
            if (contest.getReplayError() == null) continue;
            contest.setReplayError(null);
            replayContestRepository.save(contest);
        }
    }

    private boolean isPastRetentionCutoff(CombatCandidateEntity candidate, LocalDateTime retentionCutoff) {
        LocalDateTime terminalAt =
                candidate.getPromotedAt() != null ? candidate.getPromotedAt() : candidate.getResolvedAt();
        return terminalAt != null && !terminalAt.isAfter(retentionCutoff);
    }
}
