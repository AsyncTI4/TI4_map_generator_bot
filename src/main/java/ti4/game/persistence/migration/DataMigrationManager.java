package ti4.game.persistence.migration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.AliasHandler;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;

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
        migrations.put(
                "removeEronousFactions_190726_withEnded", DataMigrationManager::removeEronousFactions_190726_withEnded);
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

    // The 5 removed Eronous factions and their component ids; the models no longer exist, so ids are hardcoded here.
    private static final Set<String> ERONOUS_FACTIONS = Set.of("canto", "eidolon", "shadows", "mechi", "saera");
    private static final Map<String, String> ERONOUS_HOME_TILES =
            Map.of("canto", "as01", "eidolon", "as02", "shadows", "as03", "mechi", "as04", "saera", "as05");
    private static final Set<String> ERONOUS_PLANETS = Set.of(
            "thyolcian", "voyd", "etyr", "ecconv", "tyriaprime", "akredrite", "meccna", "gaia", "gensis", "aeva");
    private static final Set<String> ERONOUS_TECHS = Set.of(
            "cantoy",
            "cantor",
            "eidolonff",
            "eidolonb",
            "shadowssd",
            "shadowsy",
            "mechig",
            "mechiy",
            "saeracr",
            "saeray");
    private static final Set<String> ERONOUS_PNS = Set.of("cantopn", "eidolonpn", "shadowspn", "mechipn", "saerapn");
    private static final Set<String> ERONOUS_LEADERS = Set.of(
            "cantoagent",
            "cantocommander",
            "cantohero",
            "eidolonagent",
            "eidoloncommander",
            "eidolonhero",
            "shadowsagent",
            "shadowscommander",
            "shadowshero",
            "mechiagent",
            "mechicommander",
            "mechihero",
            "saeraagentprosperity",
            "saeraagentwarning",
            "saeraagentprotection",
            "saeracommander",
            "saerahero");
    private static final Set<String> ERONOUS_ABILITIES = Set.of(
            "enslave",
            "dominate",
            "seamless_integration",
            "void_tap",
            "dark_weaver",
            "abyssal_propagation",
            "creeping_shades",
            "silent_growth",
            "tomb_worlds",
            "protocols",
            "machine_cult",
            "protocol_distribution",
            "protocol_command",
            "protocol_excavation",
            "protocol_espionage",
            "protocol_conflict",
            "angelic_hosts",
            "guidance",
            "celestial_being");
    private static final Set<String> ERONOUS_UNITS = Set.of(
            "canto_flagship",
            "canto_mech",
            "eidolon_flagship",
            "eidolon_mech",
            "eidolon_fighter",
            "eidolon_fighter2",
            "shadows_flagship",
            "shadows_mech",
            "shadows_spacedock",
            "shadows_spacedock2",
            "mechi_flagship",
            "mechi_mech",
            "saera_flagship",
            "saera_mech",
            "saera_cruiser",
            "saera_cruiser2");

    public static Boolean removeEronousFactions_190726_withEnded(Game game) {
        boolean changed = false;

        // Swap any player playing a removed Eronous faction to a random official faction
        for (Player player : game.getPlayers().values()) {
            String oldFaction = player.getFaction();
            if (oldFaction == null || !ERONOUS_FACTIONS.contains(oldFaction)) continue;
            changed = true;

            FactionModel replacement = pickReplacementFaction(game);
            if (replacement == null) {
                BotLogger.warning("Migration removeEronousFactions: no replacement faction available in game "
                        + game.getName() + " for player " + player.getUserName());
                continue;
            }

            Tile oldHomeTile = null;
            String oldHomeTileId = ERONOUS_HOME_TILES.get(oldFaction);
            for (Tile tile : game.getTileMap().values()) {
                if (oldHomeTileId.equals(tile.getTileID())) {
                    oldHomeTile = tile;
                    break;
                }
            }

            player.setFaction(game, replacement.getAlias());
            player.setFactionEmoji(null);
            player.setFactionTechs(new ArrayList<>(replacement.getFactionTech()));
            player.setUnitsOwned(new HashSet<>(replacement.getUnits()));

            Set<String> ownedNotes = new HashSet<>(player.getPromissoryNotesOwned());
            ownedNotes.removeAll(ERONOUS_PNS);
            ownedNotes.addAll(replacement.getPromissoryNotes());
            player.setPromissoryNotesOwned(ownedNotes);

            if (oldHomeTile != null) {
                String newHomeTileId = AliasHandler.resolveTile(replacement.getHomeSystem());
                Tile newHomeTile = new Tile(newHomeTileId, oldHomeTile.getPosition(), oldHomeTile.getSpaceUnitHolder());
                game.setTile(newHomeTile);
                for (String planet : replacement.getHomePlanets()) {
                    String planetId = AliasHandler.resolvePlanet(planet.toLowerCase());
                    if (!player.getPlanets().contains(planetId)) {
                        player.getPlanets().add(planetId);
                    }
                }
            }
            player.setCommoditiesBase(replacement.getCommodities());
            if (player.getCommodities() > player.getCommoditiesTotal()) {
                player.setCommodities(player.getCommoditiesTotal());
            }

            BotLogger.info("Migration removeEronousFactions: in game " + game.getName() + ", swapped "
                    + player.getUserName() + " from " + oldFaction + " to "
                    + replacement.getAlias());
        }

        // Scrub stray Eronous component ids from every player (covers franken drafts and traded PNs)
        for (Player player : game.getPlayers().values()) {
            changed |= player.getLeaders().removeIf(leader -> ERONOUS_LEADERS.contains(leader.getId()));
            changed |= player.getAbilities().removeAll(ERONOUS_ABILITIES);
            changed |= player.getExhaustedAbilities().removeAll(ERONOUS_ABILITIES);
            // direct list ops on purpose: removeTech() side effects can NPE on ids with no model
            changed |= player.getTechs().removeAll(ERONOUS_TECHS);
            changed |= player.getExhaustedTechs().removeAll(ERONOUS_TECHS);
            changed |= player.getPurgedTechs().removeAll(ERONOUS_TECHS);
            changed |= player.getFactionTechs().removeAll(ERONOUS_TECHS);
            changed |= player.getUnitsOwned().removeAll(ERONOUS_UNITS);
            changed |= player.getPromissoryNotesOwned().removeAll(ERONOUS_PNS);
            changed |= player.getPromissoryNotesInPlayArea().removeAll(ERONOUS_PNS);
            for (String pn : ERONOUS_PNS) {
                if (player.getPromissoryNotes().containsKey(pn)) {
                    player.removePromissoryNote(pn);
                    changed = true;
                }
            }
            changed |= player.getPlanets().removeAll(ERONOUS_PLANETS);
            changed |= player.getExhaustedPlanets().removeAll(ERONOUS_PLANETS);
            changed |= player.getExhaustedPlanetsAbilities().removeAll(ERONOUS_PLANETS);
        }

        changed |= game.getPurgedPN().removeAll(ERONOUS_PNS);

        // Remove any Eronous home system tiles left on the map (e.g. from an abandoned setup)
        for (Tile tile : new ArrayList<>(game.getTileMap().values())) {
            if (ERONOUS_HOME_TILES.containsValue(tile.getTileID())) {
                game.removeTile(tile.getPosition());
                changed = true;
            }
        }

        return changed;
    }

    private static FactionModel pickReplacementFaction(Game game) {
        List<FactionModel> pool = Mapper.getFactionsValues().stream()
                .filter(f -> f.getSource().isOfficial())
                .filter(f -> game.isTwilightsFallMode() == (f.getSource() == ComponentSource.twilights_fall))
                .filter(f -> !"neutral".equals(f.getAlias()) && !"keleres".equals(f.getAlias()))
                .filter(f -> game.getPlayerFromColorOrFaction(f.getAlias()) == null)
                .filter(f -> game.getTile(AliasHandler.resolveTile(f.getHomeSystem())) == null)
                .collect(Collectors.toCollection(ArrayList::new));
        if (pool.isEmpty()) return null;
        Collections.shuffle(pool);
        return pool.getFirst();
    }

    private static Boolean renameGarboziaToBozgarbia_201025_withEnded(Game game) {
        Tile old = null;
        for (Tile t : game.getTileMap().values()) {
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
