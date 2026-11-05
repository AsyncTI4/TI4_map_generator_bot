package ti4.game.persistence;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.helpers.StringHelper;
import ti4.logging.BotLogger;
import ti4.settings.GlobalSettings;
import ti4.spring.service.persistence.PersistedManagedGameService;

@Deprecated(forRemoval = true)
@UtilityClass
public class DatabaseWarmupRollout {

    private static Integer differingRowsAtWarmup;
    private static String lastLoggedMessage;
    private static final Set<String> requeuedGameNames = new HashSet<>();

    static synchronized void recordWarmupCheck(DatabaseWarmupCheck check) {
        if (check.readsEnabled()) return;
        differingRowsAtWarmup = check.differingGameNames().size();
    }

    public static synchronized void advance() {
        if (differingRowsAtWarmup == null || readsEnabled() || DatabasePersistenceGate.isDisabled()) return;
        if (differingRowsAtWarmup > 0) {
            logIfNew(String.format(
                    "Database warmup reads stay OFF: at startup %s had a current database row that differs from"
                            + " its game file. Fix the mapping and raise MAPPING_VERSION.",
                    StringHelper.pluralize(differingRowsAtWarmup, "game")));
            return;
        }
        int pendingResyncs = GameDatabaseSyncPipeline.pendingResyncs();
        if (pendingResyncs > 0) {
            logIfNew(String.format(
                    "Database resync backlog: %,d of %,d game files still to sync before database warmup reads"
                            + " can be turned on.",
                    pendingResyncs, GameDatabaseSyncPipeline.queuedResyncs()));
            return;
        }
        List<String> outdatedGameNames = outdatedGameNames();
        if (!outdatedGameNames.isEmpty()) {
            requeueOnce(outdatedGameNames);
            logIfNew(String.format(
                    "Database warmup reads stay OFF: %s still %s no current database row after the resync backlog"
                            + " drained (first: %s).",
                    StringHelper.pluralize(outdatedGameNames.size(), "game"),
                    outdatedGameNames.size() == 1 ? "has" : "have",
                    outdatedGameNames.getFirst()));
            return;
        }
        GlobalSettings.setSetting(GlobalSettings.ImplementedSettings.DATABASE_WARMUP_READS_ENABLED, true);
        BotLogger.info("Every game's database row matches its game file, so `database_warmup_reads_enabled` was"
                + " turned on. Warmup will read the database from the next restart.");
    }

    private static boolean readsEnabled() {
        return GlobalSettings.ImplementedSettings.DATABASE_WARMUP_READS_ENABLED.getAsBoolean(false);
    }

    private static List<String> outdatedGameNames() {
        List<ManagedGame> managedGames = GameManager.getManagedGames();
        Map<String, String> currentRevisionKeys =
                PersistedManagedGameService.getBean().loadCurrentRevisionKeys();
        return managedGames.stream()
                .filter(game -> game.getRevisionKey() == null
                        || !Objects.equals(game.getRevisionKey(), currentRevisionKeys.get(game.getName())))
                .map(ManagedGame::getName)
                .sorted()
                .toList();
    }

    private static void requeueOnce(List<String> outdatedGameNames) {
        for (String gameName : outdatedGameNames) {
            if (requeuedGameNames.add(gameName)) {
                GameDatabaseSyncPipeline.queueSyncFromGameFile(gameName);
            }
        }
    }

    private static void logIfNew(String message) {
        if (Objects.equals(lastLoggedMessage, message)) return;
        lastLoggedMessage = message;
        BotLogger.info(message);
    }

    static synchronized void reset() {
        differingRowsAtWarmup = null;
        lastLoggedMessage = null;
        requeuedGameNames.clear();
    }
}
