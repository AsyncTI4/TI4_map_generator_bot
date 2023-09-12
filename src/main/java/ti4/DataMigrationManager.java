package ti4;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.model.FactionModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.model.TechnologyModel.TechnologyType;

public class DataMigrationManager {

    ///
    /// To add a new migration,
    /// 1. include a new static method below named
    /// migration<Description>_<current-date>(Map map)
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
    public static void runMigrations() {
        try {
            runMigration("migrateFixkeleresUnits_010823", DataMigrationManager::migrateFixkeleresUnits_010823);
            runMigration("migrateOwnedUnits_010823", DataMigrationManager::migrateOwnedUnits_010823);
            runMigration("migrateOwnedUnitsV2_210823", DataMigrationManager::migrateOwnedUnitsV2_210823);
            runMigration("migrateNullSCDeckToPoK_210823", DataMigrationManager::migrateNullSCDeckToPoK_210823);
            runMigration("migrateAbsolDeckIDs_210823", DataMigrationManager::migrateAbsolDeckIDs_210823);
            runMigration("migratePlayerStatsBlockPositions_300823", DataMigrationManager::migratePlayerStatsBlockPositions_300823);
            runMigration("migrateRelicDecksForEnigmaticStarCharts_110923", DataMigrationManager::migrateRelicDecksForEnigmaticStarChartsAndUnderscoresFromExploreDecks_110923);
            // runMigration("migrateExampleMigration_241223", (map) ->
            // migrateExampleMigration_241223(map));
        } catch (Exception e) {
            BotLogger.log("Issue running migrations:", e);
        }
    }

    /// MIGRATION: Example Migration method
    /// <Description of how data is changing, and optionally what code fix it
    /// relates to>
    public static Boolean migrateExampleMigration_241223(Game game) {
        boolean mapNeededMigrating = false;
        // Do your migration here for each non-finshed map
        // This will run once, and the map will log that it has had your migration run
        // so it doesnt re-run next time.
        return mapNeededMigrating;
    }

    /// MIGRATION: Example Migration method
    /// Remove Enigmatic and DS Star Charts from Relic Decks - no longer required since Decks were implemented
    public static Boolean migrateRelicDecksForEnigmaticStarChartsAndUnderscoresFromExploreDecks_110923(Game game) {
        boolean mapNeededMigrating = false;

        // Legacy fake relics that no longer need to be included in the deck of cards to be added
        List<String> relicDeck = new ArrayList<>(game.getAllRelics());
        if (relicDeck.remove(Constants.ENIGMATIC_DEVICE)) mapNeededMigrating = true;
        if (relicDeck.remove("starcharthazardous")) mapNeededMigrating = true;
        if (relicDeck.remove("starchartcultural")) mapNeededMigrating = true;
        if (relicDeck.remove("starchartindustrial")) mapNeededMigrating = true;
        if (relicDeck.remove("starchartfrontier")) mapNeededMigrating = true;
        game.setRelics(relicDeck);

        // Underscores in explore ID
        List<String> exploreDeck = new ArrayList<>(game.getAllExplores());
        List<String> fixedCards = new ArrayList<>();
        for (String exploreCard : exploreDeck) {
            if (exploreCard.contains("_")) {
                exploreDeck.remove(exploreCard);
                fixedCards.add(exploreCard.replaceAll("_", ""));
                mapNeededMigrating = true;
            }
        }
        exploreDeck.addAll(fixedCards);
        Collections.shuffle(exploreDeck);
        game.setExploreDeck(exploreDeck);

        return mapNeededMigrating;
    }

    /// MIGRATION: Player stats anchors implemented, but blown away all existing games.
    /// This will fix 6 player 3 ring maps anchors
    public static Boolean migratePlayerStatsBlockPositions_300823(Game activeGame) {
        Boolean mapNeededMigrating = false;
        ArrayList<String> setup6p = new ArrayList<>() {
            {
                add("301");
                add("304");
                add("307");
                add("310");
                add("313");
                add("316");
            }
        };
        ArrayList<String> setup8p = new ArrayList<>(){
            {
                add("401");
                add("404");
                add("407");
                add("410");
                add("413");
                add("416");
                add("419");
                add("422");
            }
        };
        
        List<Player> players = new ArrayList<>(activeGame.getPlayers().values());
        int playerCount = activeGame.getRealPlayers().size();

        ArrayList<String> setup;
        if (playerCount == 6 && activeGame.getRingCount() == 3) {
            setup = setup6p;
        }
        else if (playerCount == 8 && activeGame.getRingCount() == 4) {
            setup = setup8p;
        }
        else {
            return mapNeededMigrating;
        }
       
        int playerIndex = 0;
        for (Player player : players) {
            if (!player.isRealPlayer()) continue;

            String statsAnchor = player.getPlayerStatsAnchorPosition();
            if (statsAnchor != null) continue;

            player.setPlayerStatsAnchorPosition(setup.get(playerIndex));
            playerIndex++;
        }

        return mapNeededMigrating;
    }

    /// MIGRATION: Update "absol mode" games' deck IDs
    /// Only truly matters if map.resetRelics or map.resetAgendas is called 
    /// Migrated ~pbd893ish
    public static Boolean migrateAbsolDeckIDs_210823(Game game) {
        boolean mapNeededMigrating = false;

        if (game.isAbsolMode() && !"relics_absol".equals(game.getRelicDeckID()) && !"agendas_absol".equals(game.getAgendaDeckID())) {
            mapNeededMigrating = true;
            game.setRelicDeckID("relics_absol");
            game.setAgendaDeckID("agendas_absol");
        }

        return mapNeededMigrating;
    }

    /// MIGRATION: All maps should have their default scSet be "pok"
    public static Boolean migrateNullSCDeckToPoK_210823(Game game) {
        boolean mapNeededMigrating = false;

        if (game.getScSetID() == null || "null".equals(game.getScSetID())) {
            mapNeededMigrating = true;
            game.setScSetID("pok");
        }

        return mapNeededMigrating;
    }

    /// MIGRATION: Refresh owned units V2
    /// The first version of this had two issues;
    /// 1. nekro unit upgrades werent included cause the base unit didnt exist in the faction setup (updated the code below slightly)
    /// 2. the cruiser unit.json was referring to the wrong tech (this just requires us to run the same code again after changing units.json)
    /// Better to keep this code seprate so we know what was changed when. 
    ///
    public static Boolean migrateOwnedUnitsV2_210823(Game game) {
        boolean mapNeededMigrating = false;
        try {
            for (Player player : game.getRealPlayers()) {
                FactionModel factionSetupInfo = player.getFactionSetupInfo();

                // Trying to assign an accurate Faction Model for old keleres players.
                if (factionSetupInfo == null && "keleres".equals(player.getFaction())) {
                    List<FactionModel> keleresSubfactions = Mapper.getFactions().stream()
                            .filter(factionID -> factionID.startsWith("keleres") && !"keleres".equals(factionID))
                            .map(Mapper::getFactionSetup)
                            .toList();

                    // guess subfaction based on homeplanet
                    for (FactionModel factionModel : keleresSubfactions) {

                        // Check if a keleres home system is on the board.
                        Tile homesystem = game.getTile(factionModel.getHomeSystem());
                        if (homesystem == null) {
                            homesystem = game.getTile(factionModel.getHomeSystem().replace("new", ""));
                        }
                        if (homesystem != null) {
                            factionSetupInfo = Mapper.getFactionSetup(factionModel.getAlias());
                            break;
                        }

                        // When your kereles your supposed to have a '<planet-alias>k' version of the
                        // homesystem, but some games do not, so this checks for the normal planet home
                        // systems
                        // and checks if the associated faction isnt in the game.
                        List<String> subfactionHomePlanetsWithoutKeleresSuffix = factionModel.getHomePlanets()
                                .stream()
                                .map(alias -> alias.substring(0, alias.length() - 1))
                                .toList();

                        Optional<String> firstHomePlanet = subfactionHomePlanetsWithoutKeleresSuffix.stream()
                                .findFirst();
                        if (firstHomePlanet.isPresent()) {
                            Tile homeSystem = Helper.getTileFromPlanet(firstHomePlanet.get().toLowerCase(), game);
                            if (homeSystem != null) {
                                boolean isHomeSystemUsedBySomeoneElse = false;
                                for (String factionId : game.getFactions()) {
                                    FactionModel otherFactionSetup = Mapper.getFactionSetup(factionId);
                                    if (otherFactionSetup != null
                                            && otherFactionSetup.getHomeSystem().equals(homeSystem.getTileID())) {
                                        isHomeSystemUsedBySomeoneElse = true;
                                        break;
                                    }
                                }
                                if (!isHomeSystemUsedBySomeoneElse) {
                                    factionSetupInfo = factionModel;
                                }
                            }

                        }
                    }
                }

                if (factionSetupInfo == null) {
                    throw new Exception(
                            String.format("Failed to find correct faction to sync units with faction: %s, map: %s",
                                    player.getFaction(), game.getName()));
                }
                List<String> ownedUnitIDs = factionSetupInfo.getUnits();

                List<TechnologyModel> playerTechs = player.getTechs().stream().map(Mapper::getTech)
                        .filter(tech -> tech.getType() == TechnologyType.UNITUPGRADE)
                        .toList();

                for (TechnologyModel technologyModel : playerTechs) {
                    UnitModel upgradedUnit = Mapper.getUnitModelByTechUpgrade(technologyModel.getAlias());
                    if (upgradedUnit != null) {
                        if (StringUtils.isNotBlank(upgradedUnit.getBaseType())) {
                            int upgradesFromUnitIndex = ownedUnitIDs.indexOf(upgradedUnit.getBaseType());
                            if (upgradesFromUnitIndex >= 0) {
                                ownedUnitIDs.set(upgradesFromUnitIndex, upgradedUnit.getId());
                            }
                            else {
                                ownedUnitIDs.add(upgradedUnit.getId());
                            }
                        } else {
                            ownedUnitIDs.add(upgradedUnit.getId());
                        }
                    }
                }
                HashSet<String> updatedUnitIDs = new HashSet<>(ownedUnitIDs);
                if (!player.getUnitsOwned().equals(updatedUnitIDs)) {
                    mapNeededMigrating = true;
                    player.setUnitsOwned(updatedUnitIDs);
                }

            }
        } catch (Exception e) {
            BotLogger.log("Failed to migrate owned units for map" + game.getName(), e);
        }
        return mapNeededMigrating;
    }

    /// MIGRATION: Refresh owned units
    /// There was a bug recently in Player.doAdditionalThingsWhenAddingTech
    /// That didnt allow for unit upgrades to be included when there wasnt a pre-req
    /// (eg war sun)
    /// Additionally, there are historical issues of unit upgrades not being
    /// included in units_owned
    /// This migration looks to each players' faction setup for its base units
    /// Then looks to the player.techs for any upgrades to the base units to get an
    /// uptodate list of owned units.
    ///
    public static Boolean migrateOwnedUnits_010823(Game game) {
        boolean mapNeededMigrating = false;
        try {
            for (Player player : game.getRealPlayers()) {
                FactionModel factionSetupInfo = player.getFactionSetupInfo();

                // Trying to assign an accurate Faction Model for old keleres players.
                if (factionSetupInfo == null && "keleres".equals(player.getFaction())) {
                    List<FactionModel> keleresSubfactions = Mapper.getFactions().stream()
                            .filter(factionID -> factionID.startsWith("keleres") && !"keleres".equals(factionID))
                            .map(Mapper::getFactionSetup)
                            .toList();

                    // guess subfaction based on homeplanet
                    for (FactionModel factionModel : keleresSubfactions) {

                        // Check if a keleres home system is on the board.
                        Tile homesystem = game.getTile(factionModel.getHomeSystem());
                        if (homesystem == null) {
                            homesystem = game.getTile(factionModel.getHomeSystem().replace("new", ""));
                        }
                        if (homesystem != null) {
                            factionSetupInfo = Mapper.getFactionSetup(factionModel.getAlias());
                            break;
                        }

                        // When your kereles your supposed to have a '<planet-alias>k' version of the
                        // homesystem, but some games do not, so this checks for the normal planet home
                        // systems
                        // and checks if the associated faction isnt in the game.
                        List<String> subfactionHomePlanetsWithoutKeleresSuffix = factionModel.getHomePlanets()
                                .stream()
                                .map(alias -> alias.substring(0, alias.length() - 1))
                                .toList();

                        Optional<String> firstHomePlanet = subfactionHomePlanetsWithoutKeleresSuffix.stream()
                                .findFirst();
                        if (firstHomePlanet.isPresent()) {
                            Tile homeSystem = Helper.getTileFromPlanet(firstHomePlanet.get().toLowerCase(), game);
                            if (homeSystem != null) {
                                boolean isHomeSystemUsedBySomeoneElse = false;
                                for (String factionId : game.getFactions()) {
                                    FactionModel otherFactionSetup = Mapper.getFactionSetup(factionId);
                                    if (otherFactionSetup != null
                                            && otherFactionSetup.getHomeSystem().equals(homeSystem.getTileID())) {
                                        isHomeSystemUsedBySomeoneElse = true;
                                        break;
                                    }
                                }
                                if (!isHomeSystemUsedBySomeoneElse) {
                                    factionSetupInfo = factionModel;
                                }
                            }

                        }
                    }
                }

                if (factionSetupInfo == null) {
                    throw new Exception(
                            String.format("Failed to find correct faction to sync units with faction: %s, map: %s",
                                    player.getFaction(), game.getName()));
                }
                List<String> ownedUnitIDs = factionSetupInfo.getUnits();

                List<TechnologyModel> playerTechs = player.getTechs().stream().map(Mapper::getTech)
                        .filter(tech -> tech.getType() == TechnologyType.UNITUPGRADE)
                        .toList();

                for (TechnologyModel technologyModel : playerTechs) {
                    UnitModel upgradedUnit = Mapper.getUnitModelByTechUpgrade(technologyModel.getAlias());
                    if (upgradedUnit != null) {
                        if (StringUtils.isNotBlank(upgradedUnit.getUpgradesFromUnitId())) {
                            int upgradesFromUnitIndex = ownedUnitIDs.indexOf(upgradedUnit.getUpgradesFromUnitId());
                            if (upgradesFromUnitIndex >= 0) {
                                ownedUnitIDs.set(upgradesFromUnitIndex, upgradedUnit.getId());
                            }
                        } else {
                            ownedUnitIDs.add(upgradedUnit.getId());
                        }
                    }
                }
                HashSet<String> updatedUnitIDs = new HashSet<>(ownedUnitIDs);
                if (!player.getUnitsOwned().equals(updatedUnitIDs)) {
                    mapNeededMigrating = true;
                    player.setUnitsOwned(updatedUnitIDs);
                }

            }
        } catch (Exception e) {
            BotLogger.log("Failed to migrate owned units for map" + game.getName(), e);
        }
        return mapNeededMigrating;
    }

    /// MIGRATION: Fix Keleres units
    /// We've updated faction_setup.json so that the keleres factions all share the
    /// same unit type
    /// and this migration checks for any suffixed keleres units already existing
    /// incorrectly in games.
    public static Boolean migrateFixkeleresUnits_010823(Game game) {
        boolean mapNeededMigrating = false;
        for (Player player : game.getRealPlayers()) {

            List<String> ownedUnitIDs = new ArrayList<>(player.getUnitsOwned());
            for (String unitID : ownedUnitIDs) {
                int unitIndex = ownedUnitIDs.indexOf(unitID);
                if (!"keleres_flagship".equals(unitID)
                        && unitID.startsWith("keleres")
                        && unitID.endsWith("_flagship")) {
                    ownedUnitIDs.set(unitIndex, "keleres_flagship");
                    mapNeededMigrating = true;
                }
                if (!"keleres_mech".equals(unitID)
                        && unitID.startsWith("keleres")
                        && unitID.endsWith("_mech")) {
                    ownedUnitIDs.set(unitIndex, "keleres_mech");
                    mapNeededMigrating = true;
                }
            }

            player.setUnitsOwned(new HashSet<>(ownedUnitIDs));
        }
        return mapNeededMigrating;
    }

    private static void runMigration(String migrationName, Function<Game, Boolean> migrationMethod) {

        String migrationDateString = migrationName.substring(migrationName.indexOf("_") + 1);
        DateFormat format = new SimpleDateFormat("ddMMyy");
        Date migrationForGamesBeforeDate = null;
        try {
            migrationForGamesBeforeDate = format.parse(migrationDateString);
        } catch (ParseException e) {
            BotLogger.log(String.format(
                    "Migration needs a name ending in _DDMMYY (eg 251223 for 25th dec, 2023) (migration name: %s)",
                    migrationDateString), e);
        }
        List<String> migrationsAppliedThisTime = new ArrayList<>();
        Map<String, Game> loadedMaps = GameManager.getInstance().getGameNameToGame();
        for (Game game : loadedMaps.values()) {
            DateFormat mapCreatedOnFormat = new SimpleDateFormat("yyyy.MM.dd");
            Date mapCreatedOn = null;
            try {
                mapCreatedOn = mapCreatedOnFormat.parse(game.getCreationDate());
            } catch (ParseException e) {
            }
            if (mapCreatedOn == null || mapCreatedOn.after(migrationForGamesBeforeDate)) {
                continue;
            }
            boolean endVPReachedButNotEnded = game.getPlayers().values().stream()
                    .anyMatch(player -> player.getTotalVictoryPoints(game) >= game.getVp());
            if (game.isHasEnded() || endVPReachedButNotEnded) {
                continue;
            }

            if (!game.hasRunMigration(migrationName)) {
                Boolean changesMade = migrationMethod.apply(game);
                game.addMigration(migrationName);

                if (changesMade) {
                    migrationsAppliedThisTime.add(game.getName());
                }
            }
        }
        if (migrationsAppliedThisTime.size() > 0) {
            String mapNames = String.join(", ", migrationsAppliedThisTime);
            BotLogger.log(
                    String.format("Migration %s run on following maps successfully: \n%s", migrationName, mapNames));
        }
    }
}
