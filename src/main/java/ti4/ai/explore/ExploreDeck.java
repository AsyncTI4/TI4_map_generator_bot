package ti4.ai.explore;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.trace.Thinking;

@UtilityClass
public class ExploreDeck {

    public record TraitValue(String trait, double value) {}

    private static final ThreadLocal<Integer> ESTIMATING = ThreadLocal.withInitial(() -> 0);

    static boolean estimating() {
        return ESTIMATING.get() > 0;
    }

    public static double expectedValue(ExploreSite site, String trait) {
        try (Thinking.Span ignored = Thinking.enter("explore.expectedValue", () -> site.planet() + " " + trait)) {
            return averageOverDeck(site, trait);
        }
    }

    private static double averageOverDeck(ExploreSite site, String trait) {
        List<String> deck = site.game().getExploreDeck(trait);
        if (deck.isEmpty()) deck = site.game().getExploreDiscard(trait);
        if (deck.isEmpty()) return 0;
        Map<String, Double> valueByKind = new HashMap<>();
        double total = 0;
        ESTIMATING.set(ESTIMATING.get() + 1);
        try {
            for (String card : deck) {
                total += valueByKind.computeIfAbsent(CardValue.kind(card), kind -> CardValue.of(card, site));
            }
        } finally {
            ESTIMATING.set(ESTIMATING.get() - 1);
        }
        return total / deck.size();
    }

    public static Optional<TraitValue> best(ExploreSite site, Collection<String> traits) {
        return traits.stream()
                .map(trait -> new TraitValue(trait, expectedValue(site, trait)))
                .max(Comparator.comparingDouble(TraitValue::value));
    }

    public static double expectedValueOfPlanet(ExploreSite site) {
        return best(site, site.holder().getPlanetTypes()).map(TraitValue::value).orElse(0.0);
    }
}
