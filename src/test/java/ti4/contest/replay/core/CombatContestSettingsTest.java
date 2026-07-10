package ti4.contest.replay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatContestSettingsTest {

    @Test
    void noArgConstructorLoadsDevSettings() {
        CombatContestSettings settings = new CombatContestSettings();

        assertFalse(settings.isProd());
        assertTrue(settings.isEnabled());
        assertTrue(settings.getRuntime().isDevMode());
        assertEquals(100, settings.getInitialIndividualPoints());
    }

    @Test
    void devFlagFalseLoadsProdSettings() {
        CombatContestSettings settings = new CombatContestSettings(false);

        assertTrue(settings.isProd());
        assertFalse(settings.isEnabled());
        assertFalse(settings.getPromotion().isEnabled());
        assertFalse(settings.getRuntime().isDevMode());
        assertEquals(8, settings.getCandidateSelection().getTargetCandidatesPerHour());
        assertEquals(86_400, settings.getReplayExecution().getStartDelaySeconds());
        assertEquals(10, settings.getReplayExecution().getDailyLockHourCentral());
        assertEquals(0, settings.getReplayExecution().getDailyLockMinuteCentral());
        assertEquals(100, settings.getInitialIndividualPoints());
    }

    @Test
    void devFlagTrueLoadsDevSettings() {
        CombatContestSettings settings = new CombatContestSettings(true);

        assertFalse(settings.isProd());
        assertTrue(settings.isEnabled());
        assertTrue(settings.getRuntime().isDevMode());
        assertEquals(100, settings.getInitialIndividualPoints());
    }
}
