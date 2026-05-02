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
        assertTrue(settings.getRuntime().isDevMode());
        assertEquals(60, settings.getReplayExecution().getDiscussionWindowSeconds());
        assertEquals(60, settings.getReplayExecution().getSideBetWindowSeconds());
        assertEquals(15, settings.getHouseAbilities().getMentak().getPreviewLeadSeconds());
        assertEquals(1, settings.getHouseAbilities().getMinimumAbilityVotesToResolve());
    }

    @Test
    void devFlagFalseLoadsProdSettings() {
        CombatContestSettings settings = new CombatContestSettings(false);

        assertTrue(settings.isProd());
        assertFalse(settings.getRuntime().isDevMode());
        assertEquals(900, settings.getReplayExecution().getDiscussionWindowSeconds());
        assertEquals(600, settings.getReplayExecution().getSideBetWindowSeconds());
        assertEquals(900, settings.getHouseAbilities().getMentak().getPreviewLeadSeconds());
        assertEquals(3, settings.getHouseAbilities().getMinimumAbilityVotesToResolve());
    }

    @Test
    void devFlagTrueLoadsDevSettings() {
        CombatContestSettings settings = new CombatContestSettings(true);

        assertFalse(settings.isProd());
        assertTrue(settings.getRuntime().isDevMode());
        assertEquals(60, settings.getReplayExecution().getDiscussionWindowSeconds());
        assertEquals(60, settings.getReplayExecution().getSideBetWindowSeconds());
        assertEquals(15, settings.getHouseAbilities().getMentak().getPreviewLeadSeconds());
        assertEquals(1, settings.getHouseAbilities().getMinimumAbilityVotesToResolve());
    }
}
