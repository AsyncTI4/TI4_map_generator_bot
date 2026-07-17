package ti4.contest.replay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CombatPredictionPayoutTest {

    @Test
    void rewardsEvenFightsAndContrarianWinnerPredictions() {
        assertEquals(8, CombatPredictionPayout.points(90, 100));
        assertEquals(14, CombatPredictionPayout.points(70, 100));
        assertEquals(16, CombatPredictionPayout.points(50, 100));
        assertEquals(33, CombatPredictionPayout.points(30, 100));
        assertEquals(46, CombatPredictionPayout.points(20, 100));
        assertEquals(60, CombatPredictionPayout.points(10, 100));
    }

    @Test
    void returnsZeroWhenThereAreNoWinningPredictions() {
        assertEquals(0, CombatPredictionPayout.points(0, 100));
    }
}
