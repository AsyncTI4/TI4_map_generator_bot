package ti4.game.persistence.migration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;

@UtilityClass
public class DataMigrationManager {

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
    ///
    /// format option: append "_withEnded" to also run on ended games
    ///
    private static final Map<String, Function<Game, Boolean>> migrations;

    static {
        migrations = new HashMap<>();
        // migrations.put(
        //         "renameGarboziaToBozgarbia_201025_withEnded",
        //         DataMigrationManager::renameGarboziaToBozgarbia_201025_withEnded);
        // migrations.put("fixMisspelledAgendaIds_200226", DataMigrationManager::fixMisspelledAgendaIds_200226);
        // migrations.put("exampleMigration_061023", DataMigrationManager::exampleMigration_061023);
        // migrations.put(
        //         "unlockLockedAgentsBySetupState_120526",
        //         DataMigrationManager::unlockLockedAgentsBySetupState_120526);
    }

    public static void runMigrations() {
        if (migrations.isEmpty()) return;
        BotLogger.info("STARTED MIGRATIONS");
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
                        .computeIfAbsent(entry.getKey(), _ -> new ArrayList<>())
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

        BotLogger.info("FINISHED MIGRATIONS");
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
                mapCreatedOn = Instant.ofEpochMilli(managedGame.getCreationDateTime())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            } catch (Exception ignored) {
            }

            if (mapCreatedOn == null || mapCreatedOn.isAfter(migrationForGamesBeforeDate)) {
                continue;
            }

            var game = managedGame.getGame();
            if (game.hasRunMigration(migrationName)) continue;

            var changesMade = migrationMethod.apply(game);
            game.addMigration(migrationName);
            // TODO: Confirm games aren't able to make changes during this
            GameManager.save(game, "Data Migration - " + migrationName);
            if (changesMade) {
                migrationsApplied.add(game.getName());
            }
        }
        return migrationsApplied;
    }

    private static Boolean renameGarboziaToBozgarbia_201025_withEnded(Game game) {
        Tile old = null;
        for (Tile t : game.getTiles()) {
            if ("sig01".equals(t.getTileID())) {
                old = t;
                break;
            }
        }
        if (old == null) return false;

        Player p = game.getPlanetOwner("garbozia");
        if (p == null) return false;

        p.getPlanets().remove("garbozia");
        p.getExhaustedPlanets().remove("garbozia");
        p.getExhaustedPlanetsAbilities().remove("garbozia");

        p.getPlanets().add("bozgarbia");
        p.getExhaustedPlanets().add("bozgarbia");
        p.getExhaustedPlanetsAbilities().add("bozgarbia");
        return true;
    }

    private static Boolean fixMisspelledAgendaIds_200226(Game game) {
        Map<String, String> replacements = Map.of(
                "disarmamament", "disarmament",
                "absol_disarmamament", "absol_disarmament",
                "cryypter_disarmamament", "cryypter_disarmament",
                "minister_commrece", "minister_commerce",
                "senate_sancuary", "senate_sanctuary");

        return MigrationHelper.replaceAgendaCards(game, List.of(game.getAgendaDeckID()), replacements);
    }

    public static Boolean unlockLockedAgentsBySetupState_120526(Game game) {
        boolean changed = false;
        for (Player player : game.getPlayers().values()) {
            for (var leader : player.getLeaders()) {
                if (leader.isLocked() && "agent".equals(leader.getType())) {
                    leader.setLocked(false);
                    changed = true;
                }
            }
        }
        return changed;
    }
}
