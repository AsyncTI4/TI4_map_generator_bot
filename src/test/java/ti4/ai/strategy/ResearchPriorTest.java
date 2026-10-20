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

    // Sardakk players research Carrier II in 84% of games (capped), their own Exotrireme II in 82% and PDS II in 12%.
    @Test
    void followsTheSardakkResearchRates() {
        assertThat(ResearchPrior.nudge("sardakk", "cv2")).isEqualTo(ResearchPrior.WEIGHT);
        assertThat(ResearchPrior.nudge("sardakk", "exo2")).isEqualTo(ResearchPrior.WEIGHT);
        assertThat(ResearchPrior.nudge("sardakk", "dd2")).isCloseTo(0.35, within(0.01));
        assertThat(ResearchPrior.nudge("sardakk", "pds2")).isCloseTo(-0.27, within(0.01));
        assertThat(ResearchPrior.nudge("sardakk", "gls")).isCloseTo(-0.49, within(0.01));
        assertThat(ResearchPrior.nudge("sardakk", "dn2")).isZero();
    }

    // Letnev players take Non-Euclidean Shielding in 86% of games and L4 Disruptors in 10%.
    @Test
    void followsLetnevResearchRates() {
        assertThat(ResearchPrior.nudge("letnev", "nes")).isEqualTo(ResearchPrior.WEIGHT);
        assertThat(ResearchPrior.nudge("letnev", "da")).isCloseTo(0.56, within(0.01));
        assertThat(ResearchPrior.nudge("letnev", "l4")).isCloseTo(-0.29, within(0.01));
        assertThat(ResearchPrior.nudge("letnev", "sd2")).isCloseTo(-0.48, within(0.01));
        assertThat(ResearchPrior.nudge("letnev", "it")).isZero();
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
