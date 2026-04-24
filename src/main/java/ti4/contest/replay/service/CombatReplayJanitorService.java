package ti4.contest.replay.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.*;
import ti4.contest.replay.entities.*;
import ti4.contest.replay.repository.*;

@Service
@RequiredArgsConstructor
/**
 * Cleans up stale replay data and expires candidates that were never promoted in time.
 */
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
        candidateRepository
                .findByPromotionStatusAndResolvedAtBefore(
                        CombatCandidatePromotionStatus.PENDING, now.minusHours(expirationLookbackHours))
                .stream()
                .filter(candidate -> candidate.getStatus() == CombatCandidateStatus.RESOLVED)
                .forEach(candidate -> {
                    candidate.setPromotionStatus(CombatCandidatePromotionStatus.EXPIRED);
                    candidateRepository.save(candidate);
                });
    }

    private void deleteExpiredCandidateEvents(LocalDateTime now) {
        LocalDateTime retentionCutoff = now.minusDays(settings.getRetention().getEventRetentionDays());
        List<CombatCandidateEventEntity> oldEvents = candidateEventRepository.findByOccurredAtBefore(retentionCutoff);
        if (oldEvents.isEmpty()) return;

        List<Long> candidateIds = oldEvents.stream()
                .map(CombatCandidateEventEntity::getCandidateId)
                .distinct()
                .toList();
        Map<Long, CombatCandidateEntity> candidatesById = candidateRepository.findAllById(candidateIds).stream()
                .collect(Collectors.toMap(CombatCandidateEntity::getId, c -> c));
        Map<Long, CombatReplayContestEntity> contestsByCandidateId = candidateIds.stream()
                .map(id -> replayContestRepository.findByCandidateId(id).orElse(null))
                .filter(c -> c != null)
                .collect(Collectors.toMap(CombatReplayContestEntity::getCandidateId, c -> c));

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
        replayContestRepository.findAll().stream()
                .filter(contest -> contest.getReplayStatus() == CombatContestReplayStatus.COMPLETED)
                .filter(contest -> contest.getReplayError() != null)
                .forEach(contest -> {
                    contest.setReplayError(null);
                    replayContestRepository.save(contest);
                });
    }

    private boolean isPastRetentionCutoff(CombatCandidateEntity candidate, LocalDateTime retentionCutoff) {
        LocalDateTime terminalAt =
                candidate.getPromotedAt() != null ? candidate.getPromotedAt() : candidate.getResolvedAt();
        return terminalAt != null && !terminalAt.isAfter(retentionCutoff);
    }
}
