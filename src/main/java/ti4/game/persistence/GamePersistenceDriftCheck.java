package ti4.game.persistence;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.spring.service.persistence.PersistedManagedGameService;

@UtilityClass
public class GamePersistenceDriftCheck {

    private static final int MAX_LISTED_GAMES = 10;

    @Getter
    @RequiredArgsConstructor
    enum Finding {
        DATABASE_ROW_MISSING("No current database row", true),
        DATABASE_ROW_OUT_OF_DATE("Database row out of date", true),
        MANAGED_GAME_MISSING("ManagedGame missing", true),
        MANAGED_GAME_OUT_OF_DATE("ManagedGame out of date", true),
        NO_FILE_REVISION_KEY("Game file has no revision key", false),
        UNREADABLE_GAME_FILE("Game file could not be loaded", false),
        CHECK_FAILED("Check failed", false),
        CHANGED_DURING_CHECK("Changed during the check, so skipped", false);

        private final String label;
        private final boolean repairable;

        boolean isDrift() {
            return this != CHANGED_DURING_CHECK;
        }

        boolean concernsDatabaseRow() {
            return this == DATABASE_ROW_MISSING || this == DATABASE_ROW_OUT_OF_DATE;
        }

        boolean concernsManagedGame() {
            return this == MANAGED_GAME_MISSING || this == MANAGED_GAME_OUT_OF_DATE;
        }
    }

    public static String checkAndRepair() {
        long startedAt = System.currentTimeMillis();
        Map<String, String> rowRevisionKeys =
                PersistedManagedGameService.getBean().loadRevisionKeys();
        List<String> gameNames = GameManager.getGameNames();
        Report report = new Report();
        try (ExecutorService executorService =
                Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)) {
            gameNames.forEach(gameName -> executorService.submit(() -> {
                try {
                    report.add(gameName, checkAndRepairGame(gameName, rowRevisionKeys.get(gameName), startedAt));
                } catch (Exception e) {
                    report.addFailure(gameName, e);
                }
            }));
        }
        report.logFirstFailure();
        return report.summary(gameNames.size(), System.currentTimeMillis() - startedAt);
    }

    static Set<Finding> checkAndRepairGame(String gameName, @Nullable String rowRevisionKey, long startedAt) {
        String fileRevisionKey = GameRevisionKeyFile.read(gameName);
        ManagedGame managedGame = GameManager.peekManagedGame(gameName);
        Set<Finding> findings = compare(
                fileRevisionKey,
                rowRevisionKey,
                managedGame != null,
                managedGame == null ? null : managedGame.getRevisionKey());
        if (findings.isEmpty()) return findings;
        if (wasChangedAfter(gameName, startedAt)) return EnumSet.of(Finding.CHANGED_DURING_CHECK);
        if (findings.stream().noneMatch(Finding::isRepairable)) return findings;
        return repair(gameName, fileRevisionKey, findings);
    }

    static Set<Finding> compare(
            @Nullable String fileRevisionKey,
            @Nullable String rowRevisionKey,
            boolean hasManagedGame,
            @Nullable String managedGameRevisionKey) {
        if (StringUtils.isBlank(fileRevisionKey)) return EnumSet.of(Finding.NO_FILE_REVISION_KEY);
        Set<Finding> findings = EnumSet.noneOf(Finding.class);
        if (rowRevisionKey == null) {
            findings.add(Finding.DATABASE_ROW_MISSING);
        } else if (!fileRevisionKey.equals(rowRevisionKey)) {
            findings.add(Finding.DATABASE_ROW_OUT_OF_DATE);
        }
        if (!hasManagedGame) {
            findings.add(Finding.MANAGED_GAME_MISSING);
        } else if (!fileRevisionKey.equals(managedGameRevisionKey)) {
            findings.add(Finding.MANAGED_GAME_OUT_OF_DATE);
        }
        return findings;
    }

    // TODO: Like GameManager.reload, a save landing between this load and the repair can be overwritten in the
    // ManagedGame cache and the database queue by this older state, until that game's next save.
    private static Set<Finding> repair(String gameName, String fileRevisionKey, Set<Finding> findings) {
        Game game = GameLoadService.load(gameName);
        if (game == null) return EnumSet.of(Finding.UNREADABLE_GAME_FILE);
        if (!fileRevisionKey.equals(game.getRevisionKey())) return EnumSet.of(Finding.CHANGED_DURING_CHECK);
        ManagedGameState managedGameState = ManagedGameState.of(game);
        if (findings.stream().anyMatch(Finding::concernsManagedGame)) {
            GameManager.replaceManagedGame(managedGameState);
        }
        if (findings.stream().anyMatch(Finding::concernsDatabaseRow)) {
            GameDatabaseSyncPipeline.queueSync(game, managedGameState);
        }
        return findings;
    }

    private static boolean wasChangedAfter(String gameName, long timestamp) {
        return Storage.getGameFile(gameName + Constants.TXT).lastModified() > timestamp;
    }

    static final class Report {

        private final Map<Finding, List<String>> gameNamesByFinding = new EnumMap<>(Finding.class);
        private int driftedGames;
        private String firstFailedGameName;
        private Exception firstFailure;

        synchronized void add(String gameName, Set<Finding> findings) {
            findings.forEach(finding -> gameNamesByFinding
                    .computeIfAbsent(finding, _ -> new ArrayList<>())
                    .add(gameName));
            if (findings.stream().anyMatch(Finding::isDrift)) driftedGames++;
        }

        synchronized void addFailure(String gameName, Exception failure) {
            add(gameName, EnumSet.of(Finding.CHECK_FAILED));
            if (firstFailure != null) return;
            firstFailedGameName = gameName;
            firstFailure = failure;
        }

        synchronized void logFirstFailure() {
            if (firstFailure == null) return;
            BotLogger.error(
                    "The persistence drift check failed on " + firstFailedGameName + " and possibly other games.",
                    firstFailure);
        }

        synchronized String summary(int checkedGames, long elapsedMillis) {
            StringBuilder summary = new StringBuilder(String.format(
                    "Checked %,d games against their database rows and ManagedGames in %,d ms: %,d off.",
                    checkedGames, elapsedMillis, driftedGames));
            gameNamesByFinding.forEach((finding, gameNames) -> summary.append(describe(finding, gameNames)));
            if (gameNamesByFinding.keySet().stream().anyMatch(Finding::isRepairable)) {
                summary.append("\nDatabase rows were queued to re-sync from their game files,"
                        + " and ManagedGames were rebuilt from them.");
            }
            return summary.toString();
        }

        private static String describe(Finding finding, List<String> gameNames) {
            List<String> sortedGameNames = gameNames.stream().sorted().toList();
            String listed = String.join(", ", sortedGameNames.subList(0, Math.min(MAX_LISTED_GAMES, gameNames.size())));
            String more = gameNames.size() > MAX_LISTED_GAMES
                    ? String.format(" and %,d more", gameNames.size() - MAX_LISTED_GAMES)
                    : "";
            String notRepaired = finding.isRepairable() || !finding.isDrift() ? "" : " (not repaired)";
            return String.format(
                    "\n- %s: %,d%s: %s%s", finding.getLabel(), gameNames.size(), notRepaired, listed, more);
        }
    }
}
