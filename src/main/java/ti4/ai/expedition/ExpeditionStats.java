package ti4.ai.expedition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ResourceHelper;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;

@UtilityClass
public class ExpeditionStats {

    public static final double KIND_NUDGE_CAP = 1.0;
    static final double PRIOR_GAMES = 200;
    static final double PRIOR_LIFT = 0.01;
    static final double KIND_PRIOR_GAMES = 100;
    static final double NUDGE_PER_POINT = 0.2;
    private static final String RESOURCE = "ai/expedition_stats.json";
    private static final String NONE = "none";
    private static final String ANY = "any";
    private static final Map<String, String> SAME_FACTION = Map.of("firmament", "obsidian");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snapshot(Map<String, Map<String, List<Integer>>> factions) {}

    private static volatile Snapshot snapshot;

    public static double breakthroughLiftPoints(String faction) {
        Optional<Map<String, List<Integer>>> rates = ratesOf(faction);
        if (rates.isEmpty() || !rates.get().containsKey(NONE) || !rates.get().containsKey(ANY)) {
            return PRIOR_LIFT * 100;
        }
        double withBreakthrough = rate(rates.get().get(ANY));
        double without = (wins(rates.get().get(NONE)) + PRIOR_GAMES * (withBreakthrough - PRIOR_LIFT))
                / (games(rates.get().get(NONE)) + PRIOR_GAMES);
        return 100 * (withBreakthrough - without);
    }

    public static double kindNudge(String faction, ExpeditionKind kind) {
        Optional<Map<String, List<Integer>>> rates = ratesOf(faction);
        if (rates.isEmpty() || !rates.get().containsKey(kind.id())) return 0;
        double wins = 0;
        double games = 0;
        for (ExpeditionKind each : ExpeditionKind.values()) {
            List<Integer> record = rates.get().get(each.id());
            if (record == null) continue;
            wins += wins(record);
            games += games(record);
        }
        if (games <= 0) return 0;
        double mean = wins / games;
        List<Integer> own = rates.get().get(kind.id());
        double shrunk = (wins(own) + KIND_PRIOR_GAMES * mean) / (games(own) + KIND_PRIOR_GAMES);
        return Math.clamp(NUDGE_PER_POINT * 100 * (shrunk - mean), -KIND_NUDGE_CAP, KIND_NUDGE_CAP);
    }

    private static Optional<Map<String, List<Integer>>> ratesOf(String faction) {
        String key = SAME_FACTION.getOrDefault(faction, faction);
        return Optional.ofNullable(snapshot().factions().get(key));
    }

    private static double rate(List<Integer> record) {
        return games(record) <= 0 ? 0 : wins(record) / games(record);
    }

    private static double wins(List<Integer> record) {
        return record.get(0);
    }

    private static double games(List<Integer> record) {
        return record.get(1);
    }

    private static Snapshot snapshot() {
        Snapshot loaded = snapshot;
        if (loaded != null) return loaded;
        synchronized (ExpeditionStats.class) {
            if (snapshot == null) snapshot = read();
            return snapshot;
        }
    }

    private static Snapshot read() {
        Snapshot empty = new Snapshot(Map.of());
        String path = ResourceHelper.getDataFile(RESOURCE);
        if (path == null) {
            BotLogger.warning("The expedition statistics file " + RESOURCE + " was not found.");
            return empty;
        }
        try (InputStream input = Files.newInputStream(Path.of(path))) {
            Snapshot read = JsonMapperManager.basic().readValue(input, Snapshot.class);
            return read.factions() == null ? empty : read;
        } catch (IOException | RuntimeException e) {
            BotLogger.error("Could not read the expedition statistics file " + RESOURCE, e);
            return empty;
        }
    }
}
