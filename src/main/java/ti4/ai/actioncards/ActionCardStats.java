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
public class ActionCardStats {

    private static final String RESOURCE = "ai/action_card_stats.json";
    private static final Snapshot EMPTY = new Snapshot(0, 0, new Average(0, 0, 0, 0), List.of());

    private static volatile Map<String, CardStats> byName;
    private static volatile Snapshot snapshot;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CardStats(
            String name,
            int copies,
            double impact,
            boolean thin,
            double winRate,
            double playRate,
            double cancelRate,
            int wins,
            int plays,
            int cancels,
            Integer replays,
            Integer gamesReplayed) {

        public boolean isSingleCopy() {
            return copies == 1;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Average(double impact, double cancelRateWeighted, double playRate, double baselineWinRate) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snapshot(int estimatedDrawsPerCopy, int replayGames, Average average, List<CardStats> cards) {}

    public static Optional<CardStats> byName(String name) {
        return Optional.ofNullable(index().get(name));
    }

    public static Snapshot snapshot() {
        load();
        return snapshot;
    }

    public static Average average() {
        return snapshot().average();
    }

    public static List<CardStats> all() {
        return snapshot().cards();
    }

    private static Map<String, CardStats> index() {
        load();
        return byName;
    }

    private static synchronized void load() {
        if (byName != null) return;
        Snapshot loaded = read();
        Map<String, CardStats> index = new HashMap<>();
        for (CardStats card : loaded.cards()) index.put(card.name(), card);
        snapshot = loaded;
        byName = index;
    }

    private static Snapshot read() {
        String path = ResourceHelper.getDataFile(RESOURCE);
        if (path == null) {
            BotLogger.warning("The action card statistics file " + RESOURCE + " was not found.");
            return EMPTY;
        }
        try (InputStream input = Files.newInputStream(Path.of(path))) {
            return JsonMapperManager.basic().readValue(input, Snapshot.class);
        } catch (IOException | RuntimeException e) {
            BotLogger.error("Could not read the action card statistics file " + RESOURCE, e);
            return EMPTY;
        }
    }
}
