package ti4.ai.actioncards;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ResourceHelper;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;

@UtilityClass
public class ExpertTiers {

    private static final String RESOURCE = "ai/action_card_expert_tiers.json";
    private static final Map<String, double[]> STRENGTH_RANGE = Map.of(
            "S", new double[] {2.2, 1.8},
            "A", new double[] {1.7, 1.4},
            "B", new double[] {1.35, 1.05},
            "C", new double[] {1.0, 0.8},
            "D", new double[] {0.75, 0.45});

    private static volatile Map<String, Double> strengthByName;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankedCard(String name, String tier, int rankInTier) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ranking(List<RankedCard> cards) {}

    public static Optional<Double> strengthOfName(String name) {
        return Optional.ofNullable(strengths().get(name));
    }

    public static boolean isRanked(String name) {
        return strengths().containsKey(name);
    }

    static Map<String, Double> strengthsFor(List<RankedCard> cards) {
        Map<String, Long> sizes = new HashMap<>();
        for (RankedCard card : cards) sizes.merge(card.tier(), 1L, Long::sum);
        Map<String, Double> strengths = new HashMap<>();
        for (RankedCard card : cards) {
            double[] range = STRENGTH_RANGE.get(card.tier());
            if (range == null) continue;
            long size = sizes.get(card.tier());
            double position = size <= 1 ? 0 : (double) (card.rankInTier() - 1) / (size - 1);
            strengths.put(card.name(), range[0] + position * (range[1] - range[0]));
        }
        return strengths;
    }

    private static Map<String, Double> strengths() {
        Map<String, Double> loaded = strengthByName;
        if (loaded != null) return loaded;
        synchronized (ExpertTiers.class) {
            if (strengthByName == null) strengthByName = strengthsFor(read().cards());
            return strengthByName;
        }
    }

    private static Ranking read() {
        String path = ResourceHelper.getDataFile(RESOURCE);
        if (path == null) {
            BotLogger.warning("The expert action card tiers file " + RESOURCE + " was not found.");
            return new Ranking(List.of());
        }
        try (InputStream input = Files.newInputStream(Path.of(path))) {
            return JsonMapperManager.basic().readValue(input, Ranking.class);
        } catch (IOException | RuntimeException e) {
            BotLogger.error("Could not read the expert action card tiers file " + RESOURCE, e);
            return new Ranking(List.of());
        }
    }
}
