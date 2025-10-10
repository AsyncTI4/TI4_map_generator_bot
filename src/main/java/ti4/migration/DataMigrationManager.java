package ti4.migration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.helper.GameHelper;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.logging.BotLogger;

@UtilityClass
public class DataMigrationManager {

    private static final DateTimeFormatter MIGRATION_DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyy");

    // Sept 29 2025
    // We messed up how turn timers are tracked in Nucleus drafts, so
    // we're going to zero-out the bad timer data for those players.
    // Example of the kind of turn time we're trying to fix: 1759106912580
    private static Boolean migrateLongTurnTimes_091025_withEnded(Game game) {
        if (game == null) return false;

        // The earliest date Nucleus drafts were available: Sept 29 2025
        // In epoch-milliseconds: 1759104060000
        long endedDate = 1759104060000L;
        if (game.isHasEnded() && game.getEndedDate() < endedDate) return false;

        boolean changesMade = false;

        if (game.getPlayers() == null || game.getPlayers().isEmpty()) return false;
        for (Player p : game.getPlayers().values()) {
            if (p == null) continue;
            long turnTimeMilliseconds = p.getTotalTurnTime();
            long filtertime = 1000L * 60L * 60L * 1000L; // 1,000 hours
            if (turnTimeMilliseconds < filtertime) continue;

            p.setTotalTurnTime(0L);
            p.setNumberOfTurns(1);
            changesMade = true;
        }
        return changesMade;
    }

    ///
    /// To add a new migration,
    /// 1. include a new static method below named
    /// migration<Description>_<current-date-DDMMYY>(Map map)
    /// 2. Add a line at the bottom of runMigrations() below, including the name of
    /// your migration & the method itself
    ///
    /// NB: The method will be run against EVERY game map that is still current
    /// which means you might need to do different checks depending on the age of
    /// the game & how the saved data was structured for older games.
    ///
    /// Its worth getting a complete list of old game data and making sure your
    /// migration code
    /// runs properly on all before deploying to the main server.
    ///
    /// format: migrationName_DDMMYY
    /// The migration will be run on any game created on or before that date
    /// Migration will not be run on finished games
    ///
    /// format option: append "_withEnded" to also run on ended games
    ///
    private static final Map<String, Function<Game, Boolean>> migrations;

    static {
        migrations = new HashMap<>();
        migrations.put(
                "migrateLongTurnTimes_091025_withEnded", DataMigrationManager::migrateLongTurnTimes_091025_withEnded);
        // migrations.put("exampleMigration_061023", DataMigrationManager::exampleMigration_061023);
    }

    public static boolean runMigrations() {
        if (migrations.isEmpty()) return false;
        Map<String, List<String>> migrationNamesToAppliedGameNames = new HashMap<>();

        try {
            Map<String, Optional<LocalDate>> migrationNamesToCutoffDates = migrations.entrySet().stream()
                    .collect(Collectors.toMap(Entry::getKey, entry -> getMigrationForGamesBeforeDate(entry.getKey())));

            for (Entry<String, Function<Game, Boolean>> entry : migrations.entrySet()) {
                LocalDate migrationCutoffDate =
                        migrationNamesToCutoffDates.get(entry.getKey()).orElse(null);
                if (migrationCutoffDate == null) continue;

                Boolean migrateEndedGames = getMigrationForGamesEnded(entry.getKey());

                var migratedGames = migrateGames(
                        GameManager.getManagedGames(),
                        entry.getKey(),
                        entry.getValue(),
                        migrationCutoffDate,
                        migrateEndedGames);
                migrationNamesToAppliedGameNames
                        .computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                        .addAll(migratedGames);
            }
        } catch (Exception e) {
            BotLogger.error("Issue running migrations:", e);
        }

        for (Entry<String, List<String>> entry : migrationNamesToAppliedGameNames.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                String gameNames = String.join(", ", entry.getValue());
                BotLogger.info(String.format(
                        "Migration %s run on following maps successfully: \n%s", entry.getKey(), gameNames));
            }
        }
        return true;
    }

    private static Optional<LocalDate> getMigrationForGamesBeforeDate(String migrationName) {
        String migrationDateString = migrationName.substring(migrationName.indexOf('_') + 1);
        if (migrationDateString.endsWith("_withEnded")) {
            migrationDateString = migrationDateString.replace("_withEnded", "");
        }
        try {
            return Optional.of(LocalDate.parse(migrationDateString, MIGRATION_DATE_FORMATTER));
        } catch (Exception e) {
            BotLogger.error(
                    String.format(
                            "Migration needs a name ending in _DDMMYY (eg 251223 for 25th dec, 2023) (migration name: %s)",
                            migrationDateString),
                    e);
        }
        return Optional.empty();
    }

    private static Boolean getMigrationForGamesEnded(String migrationName) {
        return migrationName.endsWith("_withEnded");
    }

    private static List<String> migrateGames(
            List<ManagedGame> games,
            String migrationName,
            Function<Game, Boolean> migrationMethod,
            LocalDate migrationForGamesBeforeDate,
            Boolean migrateEndedGames) {
        List<String> migrationsApplied = new ArrayList<>();
        for (var managedGame : games) {
            if (managedGame.isHasEnded() && !migrateEndedGames) continue;

            LocalDate mapCreatedOn = null;
            try {
                mapCreatedOn = LocalDate.parse(managedGame.getCreationDate(), GameHelper.CREATION_DATE_FORMATTER);
            } catch (Exception ignored) {
            }

            if (mapCreatedOn == null || mapCreatedOn.isAfter(migrationForGamesBeforeDate)) {
                continue;
            }

            var game = managedGame.getGame();
            if (game.hasRunMigration(migrationName)) continue;

            var changesMade = migrationMethod.apply(game);
            game.addMigration(migrationName);
            GameManager.save(
                    game, "Data Migration - " + migrationName); // TODO: We should be locking since we're saving
            if (changesMade) {
                migrationsApplied.add(game.getName());
            }
        }
        return migrationsApplied;
    }
}
