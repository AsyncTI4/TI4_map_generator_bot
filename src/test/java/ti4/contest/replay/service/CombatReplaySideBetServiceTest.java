package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatContestSideBetRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayLeaderboardEntryRepository;

class CombatReplaySideBetServiceTest {

    private final CombatReplaySideBetService service = new CombatReplaySideBetService(
            new CombatContestSettings(),
            mock(CombatReplayContestRepository.class),
            mock(CombatCandidateRepository.class),
            mock(CombatContestSideBetRepository.class),
            mock(CombatReplayLeaderboardEntryRepository.class));

    @Test
    void afbSkippedAvailableForSideWithDestroyerUnlessSingleDestroyerIsFacingAssaultCannon() {
        CombatCandidateEntity candidate = candidate(1, 2, false, true);

        assertFalse(service.isAfbSkippedAvailable(candidate, "sol"));
        assertTrue(service.isAfbSkippedAvailable(candidate, "yin"));
    }

    @Test
    void afbSkippedUnavailableWithoutDestroyers() {
        CombatCandidateEntity candidate = candidate(0, 1, false, false);

        assertFalse(service.isAfbSkippedAvailable(candidate, "sol"));
        assertTrue(service.isAfbSkippedAvailable(candidate, "yin"));
    }

    private CombatCandidateEntity candidate(
            int attackerDestroyers,
            int defenderDestroyers,
            boolean attackerHasAssaultCannon,
            boolean defenderHasAssaultCannon) {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setAttackerFaction("sol");
        candidate.setDefenderFaction("yin");
        candidate.setAttackerDestroyerCount(attackerDestroyers);
        candidate.setDefenderDestroyerCount(defenderDestroyers);
        candidate.setAttackerHasAssaultCannon(attackerHasAssaultCannon);
        candidate.setDefenderHasAssaultCannon(defenderHasAssaultCannon);
        return candidate;
    }
}
