package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import ti4.ai.brain.StrategyCard;

// Nekro players almost never take Imperial in round 1 and take Warfare most; by round 4 Imperial is a common pick.
class PickPriorTest {

    @Test
    void followsTheRecordedPickRates() {
        assertThat(PickPrior.nudge(StrategyCard.IMPERIAL, 1)).isCloseTo(-0.95, within(0.01));
        assertThat(PickPrior.nudge(StrategyCard.WARFARE, 1)).isCloseTo(0.96, within(0.01));
        assertThat(PickPrior.nudge(StrategyCard.IMPERIAL, 4)).isCloseTo(0.54, within(0.01));
    }

    // Never more than an eighth of a point either way, rounds past the data reuse the last row, homebrew gets none.
    @Test
    void staysWithinTheWeightAndClampsRounds() {
        for (StrategyCard card : StrategyCard.values()) {
            for (int round = 0; round <= 9; round++) {
                assertThat(Math.abs(PickPrior.nudge(card, round))).isLessThanOrEqualTo(CardValueScale.PRIOR_WEIGHT);
            }
        }
        assertThat(PickPrior.nudge(StrategyCard.POLITICS, 9)).isEqualTo(PickPrior.nudge(StrategyCard.POLITICS, 6));
        assertThat(PickPrior.nudge(StrategyCard.OTHER, 3)).isZero();
    }
}
