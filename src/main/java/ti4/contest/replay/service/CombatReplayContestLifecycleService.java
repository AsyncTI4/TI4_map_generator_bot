package ti4.contest.replay.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;

/**
 * Stable facade for combat replay lifecycle jobs.
 */
@Service
@RequiredArgsConstructor
public class CombatReplayContestLifecycleService {

    private final CombatReplayPromotionService promotionService;
    private final CombatReplayExecutionService executionService;

    public void promoteBestCandidateIfDue() {
        promotionService.promoteBestCandidateIfDue();
    }

    void setClock(Clock clock) {
        promotionService.setClock(clock);
    }

    public ForcePromoteResult forcePromoteCandidate(Long candidateId) {
        return promotionService.forcePromoteCandidate(candidateId);
    }

    public void runReplayTick() {
        executionService.runReplayTick();
    }

    static LocalDateTime computeNextReplayAt(
            LocalDateTime replayedAt, CombatCandidateEventEntity currentEvent, CombatCandidateEventEntity nextEvent) {
        return CombatReplayExecutionService.computeNextReplayAt(replayedAt, currentEvent, nextEvent);
    }

    static LocalDateTime computeNextReplayAt(
            LocalDateTime replayedAt,
            CombatCandidateEventEntity currentEvent,
            CombatCandidateEventEntity nextEvent,
            Duration maxReplayEventGap) {
        return CombatReplayExecutionService.computeNextReplayAt(replayedAt, currentEvent, nextEvent, maxReplayEventGap);
    }

    public record ForcePromoteResult(boolean promoted, String reason, CombatReplayContestEntity contest) {

        static ForcePromoteResult promoted(CombatReplayContestEntity contest) {
            return new ForcePromoteResult(true, null, contest);
        }

        static ForcePromoteResult rejected(String reason) {
            return new ForcePromoteResult(false, reason, null);
        }

        static ForcePromoteResult rejected(String reason, CombatReplayContestEntity contest) {
            return new ForcePromoteResult(false, reason, contest);
        }
    }
}
