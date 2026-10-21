package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ti4.model.DeckModel;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

/**
 * Tests ensuring that relic drawing and shuffling follow a proper uniform random distribution.
 * Validates that players do not experience skewed draw probabilities for specific relics
 * (such as The Silver Flame) relative to any other relic in the deck.
 */
class RelicDrawDistributionTest extends BaseTi4Test {

    private static final String THUNDERS_EDGE_RELIC_DECK = "relics_pok_te";
    private static final String POK_RELIC_DECK = "relics_pok";
    private static final String SILVER_FLAME = "thesilverflame";

    @Test
    @DisplayName("Verify that drawn relics in Thunder's Edge + PoK follow a uniform random distribution")
    void firstRelicDrawInThundersEdgeDeckFollowsUniformDistribution() {
        Game game = new Game();
        DeckModel relicDeckModel = Mapper.getDeck(THUNDERS_EDGE_RELIC_DECK);
        assertThat(relicDeckModel).isNotNull();
        assertThat(relicDeckModel.getCardIDs()).contains(SILVER_FLAME);

        int relicCount = relicDeckModel.getCardCount();
        int totalDrawTrials = 46_000; // 2,000 expected draws per relic for 23 relics
        double expectedPerRelic = (double) totalDrawTrials / relicCount;

        Map<String, Integer> drawCounts = new HashMap<>();
        for (String relicId : relicDeckModel.getCardIDs()) {
            drawCounts.put(relicId, 0);
        }

        for (int i = 0; i < totalDrawTrials; i++) {
            game.setRelicDeckID(THUNDERS_EDGE_RELIC_DECK);
            game.resetRelics();
            String drawnRelic = game.drawRelic();
            drawCounts.put(drawnRelic, drawCounts.get(drawnRelic) + 1);
        }

        // Pearson's Chi-Square Goodness-of-Fit Test for Discrete Uniform Distribution
        // Degrees of freedom df = relicCount - 1 = 22
        // Critical value at alpha = 0.001 for df = 22 is ~48.27
        double chiSquare = 0.0;
        double stdDev = Math.sqrt(totalDrawTrials * (1.0 / relicCount) * (1.0 - (1.0 / relicCount)));

        for (String relicId : relicDeckModel.getCardIDs()) {
            int observed = drawCounts.get(relicId);
            double diff = observed - expectedPerRelic;
            chiSquare += (diff * diff) / expectedPerRelic;

            // Every individual relic must stay within 5 standard deviations of the mean
            assertThat(observed)
                    .as("Observed draw count for relic '%s' should be close to expected %f", relicId, expectedPerRelic)
                    .isBetween((int) (expectedPerRelic - 5 * stdDev), (int) (expectedPerRelic + 5 * stdDev));
        }

        // Assert that the overall distribution fits a uniform distribution with high statistical confidence
        assertThat(chiSquare)
                .as(
                        "Chi-square statistic %f should not exceed critical value for df = %d at alpha = 0.001",
                        chiSquare, relicCount - 1)
                .isLessThan(55.0);

        // Explicitly assert that Silver Flame is not drawn disproportionately more often than expected
        int silverFlameDraws = drawCounts.get(SILVER_FLAME);
        double silverFlameProportion = (double) silverFlameDraws / totalDrawTrials;
        double expectedProportion = 1.0 / relicCount;
        assertThat(silverFlameProportion)
                .as("Silver Flame draw proportion should match expected uniform probability of ~%f", expectedProportion)
                .isCloseTo(expectedProportion, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("Verify that vanilla PoK relic deck draws follow a uniform random distribution")
    void pokRelicDeckDrawsFollowUniformDistribution() {
        Game game = new Game();
        DeckModel relicDeckModel = Mapper.getDeck(POK_RELIC_DECK);
        assertThat(relicDeckModel).isNotNull();

        int relicCount = relicDeckModel.getCardCount();
        int totalDrawTrials = 32_000; // 2,000 expected draws per relic for 16 relics
        double expectedPerRelic = (double) totalDrawTrials / relicCount;

        Map<String, Integer> drawCounts = new HashMap<>();
        for (String relicId : relicDeckModel.getCardIDs()) {
            drawCounts.put(relicId, 0);
        }

        for (int i = 0; i < totalDrawTrials; i++) {
            game.setRelicDeckID(POK_RELIC_DECK);
            game.resetRelics();
            String drawnRelic = game.drawRelic();
            drawCounts.put(drawnRelic, drawCounts.get(drawnRelic) + 1);
        }

        double chiSquare = 0.0;
        for (String relicId : relicDeckModel.getCardIDs()) {
            int observed = drawCounts.get(relicId);
            double diff = observed - expectedPerRelic;
            chiSquare += (diff * diff) / expectedPerRelic;
        }

        // Degrees of freedom df = 16 - 1 = 15. Critical value at alpha = 0.001 is ~37.7
        assertThat(chiSquare)
                .as("Chi-square statistic %f should indicate uniform distribution for PoK relic deck", chiSquare)
                .isLessThan(45.0);
    }

    @Test
    @DisplayName("Verify that sequential draws from a single deck yield every relic exactly once without duplicates")
    void sequentialDrawsFromSingleDeckYieldAllRelicsWithoutDuplicates() {
        Game game = new Game();
        game.setRelicDeckID(THUNDERS_EDGE_RELIC_DECK);
        game.resetRelics();

        DeckModel relicDeckModel = Mapper.getDeck(THUNDERS_EDGE_RELIC_DECK);
        int expectedCount = relicDeckModel.getCardCount();

        List<String> drawnRelics = new ArrayList<>();
        Set<String> uniqueRelics = new HashSet<>();

        for (int i = 0; i < expectedCount; i++) {
            String relic = game.drawRelic();
            assertThat(relic).isNotEmpty();
            drawnRelics.add(relic);
            uniqueRelics.add(relic);
        }

        assertThat(drawnRelics).hasSize(expectedCount);
        assertThat(uniqueRelics).hasSize(expectedCount);
        assertThat(uniqueRelics).containsExactlyInAnyOrderElementsOf(relicDeckModel.getCardIDs());
        assertThat(game.getAllRelics()).isEmpty();
        assertThat(game.drawRelic()).isEmpty();
    }

    @Test
    @DisplayName("Verify that each relic's position in the shuffled deck is uniformly distributed across all slots")
    void relicPositionInDeckFollowsUniformDistribution() {
        Game game = new Game();
        game.setRelicDeckID(THUNDERS_EDGE_RELIC_DECK);
        DeckModel relicDeckModel = Mapper.getDeck(THUNDERS_EDGE_RELIC_DECK);
        int deckSize = relicDeckModel.getCardCount();
        int iterations = 10_000;

        // Track how often Silver Flame lands in each slot 0 .. deckSize-1
        int[] silverFlameSlotCounts = new int[deckSize];
        double expectedPerSlot = (double) iterations / deckSize;

        for (int iter = 0; iter < iterations; iter++) {
            game.resetRelics();
            List<String> deck = game.getAllRelics();
            int slot = deck.indexOf(SILVER_FLAME);
            assertThat(slot).isBetween(0, deckSize - 1);
            silverFlameSlotCounts[slot]++;
        }

        double chiSquare = 0.0;
        for (int slot = 0; slot < deckSize; slot++) {
            double diff = silverFlameSlotCounts[slot] - expectedPerSlot;
            chiSquare += (diff * diff) / expectedPerSlot;
        }

        // Critical value at alpha = 0.001 for df = 22 is ~48.27
        assertThat(chiSquare)
                .as("Position distribution for Silver Flame across deck slots should be uniform (chi2 = %f)", chiSquare)
                .isLessThan(55.0);
    }

    @Test
    @DisplayName("Verify that shuffling a relic back into the deck results in equal draw probabilities")
    void shuffleRelicBackPreservesUniformDistribution() {
        Game game = new Game();
        game.setRelicDeckID(THUNDERS_EDGE_RELIC_DECK);

        int trials = 20_000;
        int drawnFirstCount = 0;

        for (int i = 0; i < trials; i++) {
            game.resetRelics();
            // Remove Silver Flame and then shuffle it back into the deck
            game.getAllRelics().remove(SILVER_FLAME);
            boolean shuffledBack = game.shuffleRelicBack(SILVER_FLAME);
            assertThat(shuffledBack).isTrue();

            String drawn = game.drawRelic();
            if (SILVER_FLAME.equals(drawn)) {
                drawnFirstCount++;
            }
        }

        int deckSize = Mapper.getDeck(THUNDERS_EDGE_RELIC_DECK).getCardCount();
        double expectedCount = (double) trials / deckSize;
        double stdDev = Math.sqrt(trials * (1.0 / deckSize) * (1.0 - (1.0 / deckSize)));

        assertThat(drawnFirstCount)
                .as("Draw count for reshuffled relic should be within statistical tolerance of uniform chance")
                .isBetween((int) (expectedCount - 5 * stdDev), (int) (expectedCount + 5 * stdDev));
    }
}
