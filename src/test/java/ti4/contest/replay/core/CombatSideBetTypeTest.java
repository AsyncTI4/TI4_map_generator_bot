package ti4.contest.replay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CombatSideBetTypeTest {

    @Test
    void usesConfiguredSideBetCost() {
        CombatContestSettings settings = new CombatContestSettings();

        assertEquals(1, settings.getSideBets().getCostPoints());
    }

    @Test
    void hacanMarketMakerDefaultsToOnePointPerTakenMarkedBet() {
        CombatContestSettings settings = new CombatContestSettings();

        assertEquals(1, settings.getHouseAbilities().getHacan().getMarketMakerPointsPerBet());
    }

    @Test
    void hacanMarkedBetsGrantFavorOnHit() {
        CombatContestSettings settings = new CombatContestSettings();

        assertEquals(10, settings.getHouseAbilities().getHacan().getSubsidyFavorOnHit());
    }

    @Test
    void devHousePhaseWindowsAreCompressed() {
        CombatContestSettings settings = new CombatContestSettings();

        assertEquals(15, settings.getHouseAbilities().getMentak().getPreviewLeadSeconds());
        assertEquals(60, settings.getReplayExecution().getDiscussionWindowSeconds());
        assertEquals(60, settings.getReplayExecution().getSideBetWindowSeconds());
    }

    @Test
    void usesConfiguredSideBetPayouts() {
        Map<CombatSideBetType, Integer> expectedPayouts = Map.of(
                CombatSideBetType.AFB_SKIPPED, 6,
                CombatSideBetType.AFB_WHIFF, 4,
                CombatSideBetType.ROUND_ONE_WHIFF, 10,
                CombatSideBetType.ROUND_ONE_SLAM, 30,
                CombatSideBetType.MORALE_BOOST, 8,
                CombatSideBetType.SHIELDS_HOLDING, 8,
                CombatSideBetType.WINNER_ONE_HP, 35);

        expectedPayouts.forEach((type, expectedPayout) -> assertEquals(expectedPayout, type.profitPoints()));
    }
}
