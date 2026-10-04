package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.helpers.DiceHelper.Die;

class DiceHelperTest {

    // The test bed fixes dice for its own presses; forced results are used in order, then rolls are random again.
    @Test
    void forcedResultsAreUsedInOrderThenRandom() {
        Deque<Integer> forced = new ArrayDeque<>(List.of(6, 1));
        List<Die> rolled = new ArrayList<>();

        DiceHelper.withForcedResults(forced, () -> rolled.addAll(DiceHelper.rollDice(5, 3)));

        assertEquals(6, rolled.get(0).getResult());
        assertTrue(rolled.get(0).isSuccess());
        assertEquals(1, rolled.get(1).getResult());
        assertFalse(rolled.get(1).isSuccess());
        assertTrue(rolled.get(2).getResult() >= 1 && rolled.get(2).getResult() <= 10);
        assertTrue(forced.isEmpty());
    }

    // Outside withForcedResults nothing is forced, even right after a forced block on the same thread.
    @Test
    void forcingEndsWithTheBlock() {
        DiceHelper.withForcedResults(new ArrayDeque<>(List.of(10, 10, 10)), () -> {});
        for (int i = 0; i < 50; i++) {
            int result = new Die(0).getResult();
            assertTrue(result >= 1 && result <= 10);
        }
        Deque<Integer> leftover = new ArrayDeque<>(List.of(3));
        DiceHelper.withForcedResults(leftover, () -> {});
        assertEquals(1, leftover.size());
    }
}
