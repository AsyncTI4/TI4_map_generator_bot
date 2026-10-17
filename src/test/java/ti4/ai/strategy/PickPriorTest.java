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

    // Sol players take Politics first (231 of 1194 games), Technology and Trade next, Imperial almost never in round 1
    // but the most from round 3 on.
    @Test
    void usesSolsOwnPickRates() {
        assertThat(PickPrior.nudge("sol", StrategyCard.POLITICS, 1)).isCloseTo(0.55, within(0.01));
        assertThat(PickPrior.nudge("sol", StrategyCard.TRADE, 1)).isCloseTo(0.37, within(0.01));
        assertThat(PickPrior.nudge("sol", StrategyCard.IMPERIAL, 1)).isCloseTo(-0.98, within(0.01));
        assertThat(PickPrior.nudge("sol", StrategyCard.LEADERSHIP, 2)).isCloseTo(0.66, within(0.01));
        assertThat(PickPrior.nudge("sol", StrategyCard.IMPERIAL, 3)).isCloseTo(0.52, within(0.01));
        assertThat(PickPrior.nudge("sol", StrategyCard.CONSTRUCTION, 1)).isNegative();
    }

    // Xxcha players take Diplomacy most in round 1 (Peace Accords) and Hacan players Trade; factions without their
    // own data use the Nekro rates.
    @Test
    void usesTheFactionsOwnPickRates() {
        assertThat(PickPrior.nudge("xxcha", StrategyCard.DIPLOMACY, 1)).isCloseTo(1.0, within(0.01));
        assertThat(PickPrior.nudge("hacan", StrategyCard.DIPLOMACY, 1)).isCloseTo(-0.52, within(0.01));
        assertThat(PickPrior.nudge("hacan", StrategyCard.TRADE, 1)).isCloseTo(0.77, within(0.01));
        assertThat(PickPrior.nudge("sardakk", StrategyCard.WARFARE, 1))
                .isEqualTo(PickPrior.nudge(StrategyCard.WARFARE, 1));
    }
}
