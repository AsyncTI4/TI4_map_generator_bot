package ti4.contest.replay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CombatSideBetTypeTest {

    @Test
    void usesConfiguredSideBetCost() {
        CombatContestSettings settings = new CombatContestSettings();

        assertEquals(1, settings.getSideBets().getCostPoints());
        assertEquals(50, settings.getSideBets().getDynamicPayoutCap());
    }

    @Test
    void usesConfiguredSideBetPayouts() {
        Map<CombatSideBetType, Integer> expectedPayouts = Map.of(
                CombatSideBetType.AFB_SKIPPED, 6,
                CombatSideBetType.AFB_WHIFF, 4,
                CombatSideBetType.ROUND_ONE_WHIFF, 10,
                CombatSideBetType.ROUND_ONE_SLAM, 30,
                CombatSideBetType.MORALE_BOOST, 12,
                CombatSideBetType.SHIELDS_HOLDING, 8,
                CombatSideBetType.DIRECT_HIT, 8,
                CombatSideBetType.FIGHTER_PROTOTYPE, 24,
                CombatSideBetType.WINNER_ONE_HP, 35);

        expectedPayouts.forEach((type, expectedPayout) -> assertEquals(expectedPayout, type.profitPoints()));
    }
}
