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
    void usesConfiguredSideBetPayouts() {
        Map<CombatSideBetType, Integer> expectedPayouts = Map.of(
                CombatSideBetType.AFB_SKIPPED, 3,
                CombatSideBetType.AFB_WHIFF, 3,
                CombatSideBetType.ROUND_ONE_WHIFF, 5,
                CombatSideBetType.ROUND_ONE_SLAM, 7,
                CombatSideBetType.MORALE_BOOST, 5,
                CombatSideBetType.SHIELDS_HOLDING, 5,
                CombatSideBetType.WINNER_ONE_HP, 15);

        expectedPayouts.forEach((type, expectedPayout) -> assertEquals(expectedPayout, type.profitPoints()));
    }
}
