package ti4.ai.explore;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ExploreDeck {

    public record TraitValue(String trait, double value) {}

    public static double expectedValue(ExploreSite site, String trait) {
        List<String> deck = site.game().getExploreDeck(trait);
        if (deck.isEmpty()) deck = site.game().getExploreDiscard(trait);
        if (deck.isEmpty()) return 0;
        Map<String, Double> valueByKind = new HashMap<>();
        double total = 0;
        for (String card : deck) {
            total += valueByKind.computeIfAbsent(CardValue.kind(card), kind -> CardValue.of(card, site));
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
