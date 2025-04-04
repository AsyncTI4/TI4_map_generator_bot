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
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.BotLogger;

@UtilityClass
public class DataMigrationManager {

    private static final DateTimeFormatter MAP_CREATED_ON_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter MIGRATION_DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyy");

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
    private static final Map<String, Function<Game, Boolean>> migrations;
    static {
        migrations = new HashMap<>();
        migrations.put("removeWekkersAbsolsPoliticalSecret_220125", MigrationHelper::removeWekkersAbsolsPoliticalSecrets);
        migrations.put("removeWekkersAbsolsPoliticalSecretAgain_220125", MigrationHelper::removeWekkersAbsolsPoliticalSecretsAgain);
        //migrations.put("exampleMigration_061023", DataMigrationManager::exampleMigration_061023);
    }

    public static boolean runMigrations() {
        if (migrations.isEmpty()) return false;
        Map<String, List<String>> migrationNamesToAppliedGameNames = new HashMap<>();

        try {
            Map<String, Optional<LocalDate>> migrationNamesToCutoffDates = migrations.entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey, entry -> getMigrationForGamesBeforeDate(entry.getKey())));

            for (Entry<String, Function<Game, Boolean>> entry : migrations.entrySet()) {
                LocalDate migrationCutoffDate = migrationNamesToCutoffDates.get(entry.getKey()).orElse(null);
                if (migrationCutoffDate == null) continue;

                var migratedGames = migrateGames(GameManager.getManagedGames(), entry.getKey(), entry.getValue(), migrationCutoffDate);
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
                BotLogger.info(String.format("Migration %s run on following maps successfully: \n%s", entry.getKey(), gameNames));
            }
        }
        return true;
    }

    private static Optional<LocalDate> getMigrationForGamesBeforeDate(String migrationName) {
        String migrationDateString = migrationName.substring(migrationName.indexOf("_") + 1);
        try {
            return Optional.of(LocalDate.parse(migrationDateString, MIGRATION_DATE_FORMATTER));
        } catch (Exception e) {
            BotLogger.error(String.format(
                "Migration needs a name ending in _DDMMYY (eg 251223 for 25th dec, 2023) (migration name: %s)", migrationDateString), e);
        }
        return Optional.empty();
    }

    private static List<String> migrateGames(List<ManagedGame> games, String migrationName, Function<Game, Boolean> migrationMethod,
        LocalDate migrationForGamesBeforeDate) {
        List<String> migrationsApplied = new ArrayList<>();
        for (var managedGame : games) {
            if (managedGame.isHasEnded()) continue;

            LocalDate mapCreatedOn = null;
            try {
                mapCreatedOn = LocalDate.parse(managedGame.getCreationDate(), MAP_CREATED_ON_FORMAT);
            } catch (Exception ignored) {
            }

            if (mapCreatedOn == null || mapCreatedOn.isAfter(migrationForGamesBeforeDate)) {
                continue;
            }

            var game = managedGame.getGame();
            if (game.hasRunMigration(migrationName)) continue;

            var changesMade = migrationMethod.apply(game);
            game.addMigration(migrationName);
            GameManager.save(game, "Data Migration - " + migrationName);
            if (changesMade) {
                migrationsApplied.add(game.getName());
            }
        }
        return migrationsApplied;
    }
}
