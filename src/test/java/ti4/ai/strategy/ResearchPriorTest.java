package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

// Hacan players research Gravity Drive in 85% of games and PDS II in 3%; Xxcha players take PDS II in 62%. A rate of
// a quarter of games is neutral, and the nudge never passes the weight either way.
class ResearchPriorTest {

    @Test
    void followsEachFactionsResearchRates() {
        assertThat(ResearchPrior.nudge("hacan", "gd")).isEqualTo(ResearchPrior.WEIGHT);
        assertThat(ResearchPrior.nudge("hacan", "pds2")).isCloseTo(-0.44, within(0.01));
        assertThat(ResearchPrior.nudge("xxcha", "pds2")).isCloseTo(0.75, within(0.01));
    }

    // Sol players research Advanced Carrier II and Gravity Drive in over 90% of games, Spec Ops II in 42%, PDS II in
    // 1%.
    @Test
    void followsSolsResearchRates() {
        assertThat(ResearchPrior.nudge("sol", "ac2")).isEqualTo(ResearchPrior.WEIGHT);
        assertThat(ResearchPrior.nudge("sol", "gd")).isEqualTo(ResearchPrior.WEIGHT);
        assertThat(ResearchPrior.nudge("sol", "so2")).isCloseTo(0.33, within(0.01));
        assertThat(ResearchPrior.nudge("sol", "x89c4")).isCloseTo(0.16, within(0.01));
        assertThat(ResearchPrior.nudge("sol", "pds2")).isCloseTo(-0.48, within(0.01));
        assertThat(ResearchPrior.nudge("sol", "it")).isZero();
    }

    @Test
    void givesNothingWithoutData() {
        assertThat(ResearchPrior.nudge("nekro", "gd")).isZero();
        assertThat(ResearchPrior.nudge("hacan", "it")).isZero();
    }
}
