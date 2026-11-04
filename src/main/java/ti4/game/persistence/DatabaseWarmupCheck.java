package ti4.game.persistence;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.StringHelper;
import ti4.logging.BotLogger;
import ti4.settings.GlobalSettings;

final class DatabaseWarmupCheck {

    private static final int MAX_REPORT_LENGTH = 1800;

    private final boolean readsEnabled;
    private final AtomicInteger matchingRows = new AtomicInteger();
    private final AtomicInteger outdatedRows = new AtomicInteger();
    private final ConcurrentMap<String, List<String>> differingRows = new ConcurrentHashMap<>();

    DatabaseWarmupCheck(boolean readsEnabled) {
        this.readsEnabled = readsEnabled;
    }

    static DatabaseWarmupCheck fromSettings() {
        return new DatabaseWarmupCheck(
                GlobalSettings.ImplementedSettings.DATABASE_WARMUP_READS_ENABLED.getAsBoolean(false));
    }

    boolean readsEnabled() {
        return readsEnabled;
    }

    boolean rowMatchesFile(String gameName, @Nullable ManagedGameState persistedGame, ManagedGameState fileState) {
        if (persistedGame == null || !persistedGame.matchesRevision(fileState.revisionKey())) {
            outdatedRows.incrementAndGet();
            return false;
        }
        List<String> differingFields = differingFields(fileState, persistedGame);
        if (!differingFields.isEmpty()) {
            differingRows.put(gameName, differingFields);
            return false;
        }
        matchingRows.incrementAndGet();
        return true;
    }

    List<String> differingGameNames() {
        return differingRows.keySet().stream().sorted().toList();
    }

    void report() {
        BotLogger.info(summary());
        if (!differingRows.isEmpty()) {
            BotLogger.error(differingRowsReport());
        }
    }

    String summary() {
        String mode = readsEnabled
                ? "Database warmup reads are ON."
                : "Database warmup reads are OFF, so every game was built from its game file.";
        String counts = String.format(
                " Database rows checked against game files: %,d match, %,d outdated or missing (resync queued), %,d differ.",
                matchingRows.get(), outdatedRows.get(), differingRows.size());
        if (readsEnabled || outdatedRows.get() > 0 || !differingRows.isEmpty()) return mode + counts;
        return mode + counts + " Every row matches its game file, so `database_warmup_reads_enabled` can be turned on.";
    }

    private String differingRowsReport() {
        List<String> gameNames = differingGameNames();
        StringBuilder report = new StringBuilder(String.format(
                "**%s %s a database row with the current revision key that differs from the game file"
                        + " (a mapping bug; raise MAPPING_VERSION once fixed):**",
                StringHelper.pluralize(gameNames.size(), "game"), gameNames.size() == 1 ? "has" : "have"));
        int reported = 0;
        for (String gameName : gameNames) {
            String line = "\n- " + gameName + ": " + String.join(", ", differingRows.get(gameName));
            if (report.length() + line.length() > MAX_REPORT_LENGTH - 40) break;
            report.append(line);
            reported++;
        }
        if (reported < gameNames.size()) {
            report.append(String.format("\n...and %,d more.", gameNames.size() - reported));
        }
        return StringUtils.abbreviate(report.toString(), MAX_REPORT_LENGTH);
    }

    static List<String> differingFields(ManagedGameState expected, ManagedGameState actual) {
        List<String> differing = new ArrayList<>();
        for (RecordComponent component : ManagedGameState.class.getRecordComponents()) {
            if (!Objects.equals(valueOf(component, expected), valueOf(component, actual))) {
                differing.add(component.getName());
            }
        }
        return differing;
    }

    private static Object valueOf(RecordComponent component, ManagedGameState state) {
        try {
            return component.getAccessor().invoke(state);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read " + component.getName(), e);
        }
    }
}
