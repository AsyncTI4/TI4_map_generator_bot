package ti4.spring.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SkillTierTest {

    @Test
    void tiersCoverEveryRatingWithoutOverlap() {
        // Includes the negative tail, which real ratings do reach.
        for (long displayRating = -1000; displayRating <= 4000; displayRating += 10) {
            long rating = displayRating;
            assertThat(Arrays.stream(SkillTier.values()).filter(tier -> tier.contains(rating)))
                    .as("exactly one tier should contain %d", rating)
                    .hasSize(1);
        }
    }

    @Test
    void boundariesAreHalfOpenSoTiersMeetExactly() {
        long lowerMediumBoundary = SkillTier.MEDIUM.getMinimumDisplayRatingInclusive();
        assertThat(SkillTier.fromDisplayRating(lowerMediumBoundary - 1)).isEqualTo(SkillTier.LOWER);
        assertThat(SkillTier.fromDisplayRating(lowerMediumBoundary)).isEqualTo(SkillTier.MEDIUM);

        long mediumHigherBoundary = SkillTier.HIGHER.getMinimumDisplayRatingInclusive();
        assertThat(SkillTier.fromDisplayRating(mediumHigherBoundary - 1)).isEqualTo(SkillTier.MEDIUM);
        assertThat(SkillTier.fromDisplayRating(mediumHigherBoundary)).isEqualTo(SkillTier.HIGHER);
    }

    @Test
    void boundariesLandOnBracketEdges() {
        for (SkillTier skillTier : SkillTier.values()) {
            long minimum = skillTier.getMinimumDisplayRatingInclusive();
            if (minimum != Long.MIN_VALUE) {
                assertThat(Math.floorMod(minimum, 100))
                        .as("%s starts mid-bracket at %d", skillTier, minimum)
                        .isZero();
            }
        }
    }

    @Test
    void parsesOptionValuesAndRejectsJunk() {
        assertThat(SkillTier.fromOptionValue("HIGHER")).isEqualTo(SkillTier.HIGHER);
        assertThat(SkillTier.fromOptionValue(" lower ")).isEqualTo(SkillTier.LOWER);
        assertThat(SkillTier.fromOptionValue(null)).isNull();
        assertThat(SkillTier.fromOptionValue("")).isNull();
        assertThat(SkillTier.fromOptionValue("elite")).isNull();
    }
}
