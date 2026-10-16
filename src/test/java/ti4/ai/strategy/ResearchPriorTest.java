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

    @Test
    void givesNothingWithoutData() {
        assertThat(ResearchPrior.nudge("nekro", "gd")).isZero();
        assertThat(ResearchPrior.nudge("hacan", "it")).isZero();
    }
}
