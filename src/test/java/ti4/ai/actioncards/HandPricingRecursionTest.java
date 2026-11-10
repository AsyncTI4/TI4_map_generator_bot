package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import ti4.ai.HandPricingRecursionTable;
import ti4.testUtils.BaseTi4Test;

// Pricing the action card slice of an open expedition prices the hand, and Strategize in the hand prices its follow,
// which reaches the slice again through the Leadership tokens the seat could buy. This used to recurse until the stack
// overflowed; the hand is now priced at its card priors while it is already being priced.
class HandPricingRecursionTest extends BaseTi4Test {

    private static final Duration LIMIT = Duration.ofSeconds(60);

    private static <T> T withinLimit(Supplier<T> pricing) {
        return assertTimeoutPreemptively(LIMIT, pricing::get);
    }

    private static double strategizeKeep(HandPricingRecursionTable table) {
        return withinLimit(
                () -> ActionCardValue.keepValue(table.game, table.seat, HandPricingRecursionTable.STRATEGIZE));
    }

    private static Optional<Plan> strategizePlan(HandPricingRecursionTable table) {
        return withinLimit(() -> AiActionCards.of(HandPricingRecursionTable.STRATEGIZE)
                .orElseThrow()
                .planFor(table.game, table.seat));
    }

    @Test
    void strategizeIsKeptAtAFiniteValueWhenItsTokenMightGoToTechnology() {
        assertThat(strategizeKeep(HandPricingRecursionTable.throughTechnology()))
                .isFinite();
    }

    @Test
    void strategizeIsKeptAtAFiniteValueWhenItWouldFollowDiplomacy() {
        assertThat(strategizeKeep(HandPricingRecursionTable.throughDiplomacy())).isFinite();
    }

    @Test
    void strategizeIsPlannedWhenItsTokenMightGoToTechnology() {
        assertThat(strategizePlan(HandPricingRecursionTable.throughTechnology())
                        .map(Plan::value)
                        .orElse(0.0))
                .isFinite();
    }

    @Test
    void strategizeIsPlannedWhenItWouldFollowDiplomacy() {
        assertThat(strategizePlan(HandPricingRecursionTable.throughDiplomacy())
                        .map(Plan::value)
                        .orElse(0.0))
                .isFinite();
    }
}
