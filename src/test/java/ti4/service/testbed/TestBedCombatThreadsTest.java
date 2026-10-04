package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;

class TestBedCombatThreadsTest {

    // Names follow StartCombatService.combatThreadName: colors in fog, factions otherwise.
    @Test
    void recognisesCombatThreadsOfThisGameOnly() {
        Game game = mock(Game.class);
        when(game.getName()).thenReturn("pbd7");

        assertTrue(TestBedCombatThreads.isCombatThread(game, "pbd7-round-1-system-101-turn-1-letnev-vs-nekro"));
        assertTrue(TestBedCombatThreads.isCombatThread(game, "pbd7-round-2-system-305-turn-3-red-vs-blue-private"));
        assertFalse(TestBedCombatThreads.isCombatThread(game, "pbd70-round-1-system-101-turn-1-letnev-vs-nekro"));
        assertFalse(TestBedCombatThreads.isCombatThread(game, "pbd7-comms-blue-private"));
        assertFalse(TestBedCombatThreads.isCombatThread(game, "pbd7-cards-info-someone-private"));
    }

    // testBedDice holds space-separated results; anything that is not 1-10 is skipped.
    @Test
    void readsForcedDiceFromTheStoredValue() {
        Game game = mock(Game.class);
        when(game.getStoredValue(TestBedPress.FORCED_DICE_KEY)).thenReturn(" 10 1  0 11 x 7 ");
        assertEquals(List.of(10, 1, 7), TestBedPress.forcedDice(game));

        when(game.getStoredValue(TestBedPress.FORCED_DICE_KEY)).thenReturn("");
        assertEquals(List.of(), TestBedPress.forcedDice(game));
    }
}
