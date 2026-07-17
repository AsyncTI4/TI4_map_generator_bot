package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import ti4.contest.replay.entities.CombatCandidateEntity;

class CombatReplaySideBetServiceTest {

    private final CombatReplaySideBetPayoutService payoutService = mock(CombatReplaySideBetPayoutService.class);

    @Test
    void afbSkippedAvailableForSideWithDestroyerUnlessSingleDestroyerIsFacingAssaultCannon() {
        CombatCandidateEntity candidate = candidate(1, 2, false, true);
        CombatSideBetAvailabilityService realAvailabilityService = new CombatSideBetAvailabilityService(payoutService);

        assertFalse(realAvailabilityService.isAfbSkippedAvailable(candidate, "sol"));
        assertTrue(realAvailabilityService.isAfbSkippedAvailable(candidate, "yin"));
    }

    @Test
    void afbSkippedUnavailableWithoutDestroyers() {
        CombatCandidateEntity candidate = candidate(0, 1, false, false);
        CombatSideBetAvailabilityService realAvailabilityService = new CombatSideBetAvailabilityService(payoutService);

        assertFalse(realAvailabilityService.isAfbSkippedAvailable(candidate, "sol"));
        assertTrue(realAvailabilityService.isAfbSkippedAvailable(candidate, "yin"));
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
