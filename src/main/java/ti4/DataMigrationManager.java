package ti4;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import ti4.commands.player.ChangeColor;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

public class DataMigrationManager {

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
    public static void runMigrations() {
        try {
            runMigration("migrateRenameDSExplores_061023", DataMigrationManager::migrateRenameDSExplores_061023);
            runMigration("migrateRenameVeldyrAttachments_270923", DataMigrationManager::migrateRenameVeldyrAttachments_270923);
            runMigration("migrateGheminaAddCarrier_190923", DataMigrationManager::migrateGheminaAddCarrier_190923);
            runMigration("migrateNaaluMechsToOmega_180923", DataMigrationManager::migrateNaaluMechsToOmega_180923);
            runMigration("migrateFixkeleresUnits_010823", DataMigrationManager::migrateFixkeleresUnits_010823);
            runMigration("migrateOwnedUnits_010823", DataMigrationManager::migrateOwnedUnits_010823);
            runMigration("migrateOwnedUnitsV2_210823", DataMigrationManager::migrateOwnedUnitsV2_210823);
            runMigration("migrateNullSCDeckToPoK_210823", DataMigrationManager::migrateNullSCDeckToPoK_210823);
            runMigration("migrateAbsolDeckIDs_210823", DataMigrationManager::migrateAbsolDeckIDs_210823);
            runMigration("migratePlayerStatsBlockPositions_300823", DataMigrationManager::migratePlayerStatsBlockPositions_300823);
            runMigration("migrateRelicDecksForEnigmaticStarCharts_110923", DataMigrationManager::migrateRelicDecksForEnigmaticStarChartsAndUnderscoresFromExploreDecks_110923);
            runMigration("migrateForceShuffleAllRelicsDecks_241223", DataMigrationManager::migrateForceShuffleAllRelicsDecks_241223);
            runMigration("migrateInitializeFactionTechs_181023", DataMigrationManager::migrateInitializeFactionTechs_181023);
            runMigration("migrateRemoveOldArcaneShieldID_111223", DataMigrationManager::migrateRemoveOldArcaneShieldID_111223);
            runMigration("migrateFrankenItems_111223", DataMigrationManager::migrateFrankenItems_111223);
            runMigration("resetMinorFactionCommanders_130624", DataMigrationManager::resetMinorFactionCommanders_130624);
            runMigration("removeBadCVToken_290624", DataMigrationManager::removeBadCVToken_290624);
            runMigration("migrateCreationDate_311024", DataMigrationManager::migrateCreationDate_311024);
            runMigration("noMoreRiftset_311024", DataMigrationManager::noMoreRiftset_311024);
        } catch (Exception e) {
            BotLogger.log("Issue running migrations:", e);
        }
    }

    /// MIGRATION: Example Migration method
    /// <Description of how data is changing, and optionally what code fix it relates to>
    public static Boolean migrateExampleMigration_241223(Game game) { // method_DDMMYY where DD = Day, MM = Month, YY = Year
        // Do your migration here for each non-finshed map
        // This will run once, and the map will log that it has had your migration run so it doesnt re-run next time.
        return false;
    }

    /// MIGRATION: Add game.startedDate (long)
    public static Boolean migrateCreationDate_311024(Game game) {
        if (game.getStartedDate() < 1) {
            LocalDate localDate;
            try {
                localDate = LocalDate.parse(game.getCreationDate(), DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            } catch (DateTimeParseException e) {
                localDate = LocalDate.now();
            }
            int gameNameHash = Math.abs(game.getName().hashCode());
            int hours = gameNameHash % 24;
            int minutes = gameNameHash % 60;
            int seconds = Math.abs(game.getCustomName().hashCode()) % 60;
            var localDateTime = localDate.atTime(hours, minutes, seconds);
            game.setStartedDate(localDateTime.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli());
            return true;
        }
        return false;
    }

    /// MIGRATION: Remove old/bad arcane shield attachmentID
    public static Boolean migrateRemoveOldArcaneShieldID_111223(Game game) {
        boolean mapNeededMigrating = false;
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getTokenList().contains("attachment_arcane_shield.png")) {
                    unitHolder.removeToken("attachment_arcane_shield.png");
                    unitHolder.addToken("attachment_arc_shield.png");
                    mapNeededMigrating = true;
                }
            }
        }
        return mapNeededMigrating;
    }

    /// MIGRATION: Add faction techs to games that were created before faction techs added
    public static Boolean migrateInitializeFactionTechs_181023(Game game) {
        boolean mapNeededMigrating = false;
        for (Player player : game.getRealPlayers()) {
            if (player.getFactionTechs().isEmpty()) {
                if (player.getFactionSetupInfo() == null) continue;
                mapNeededMigrating = true;
                for (String techID : player.getFactionSetupInfo().getFactionTech()) {
                    player.addFactionTech(techID);
                }
            }
        }
        return mapNeededMigrating;
    }

    /// MIGRATION: Shuffle the damn relic decks
    public static Boolean migrateForceShuffleAllRelicsDecks_241223(Game game) {
        boolean mapNeededMigrating = false;
        Collections.shuffle(game.getAllRelics());
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
        List<String> badCards = new ArrayList<>();
        List<String> fixedCards = new ArrayList<>();
        for (String exploreCard : exploreDeck) {
            if (exploreCard.contains("_")) {
                badCards.add(exploreCard);
                fixedCards.add(exploreCard.replaceAll("_", ""));
                mapNeededMigrating = true;
            }
        }
        exploreDeck.removeAll(badCards);
        exploreDeck.addAll(fixedCards);
        Collections.shuffle(exploreDeck);
        game.setExploreDeck(exploreDeck);

        return mapNeededMigrating;
    }

    /// MIGRATION: Player stats anchors implemented, but blown away all existing games.
    /// This will fix 6 player 3 ring maps anchors
    public static Boolean migratePlayerStatsBlockPositions_300823(Game game) {
        Boolean mapNeededMigrating = false;
        List<String> setup6p = new ArrayList<>() {
            {
                add("301");
                add("304");
                add("307");
                add("310");
                add("313");
                add("316");
            }
        };
        List<String> setup8p = new ArrayList<>() {
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

        List<Player> players = new ArrayList<>(game.getPlayers().values());
        int playerCount = game.getRealPlayers().size() + game.getDummies().size();

        List<String> setup;
        if (playerCount == 6 && game.getRingCount() == 3) {
            setup = setup6p;
        } else if (playerCount == 8 && game.getRingCount() == 4) {
            setup = setup8p;
        } else {
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
                    List<FactionModel> keleresSubfactions = Mapper.getFactionIDs().stream()
                        .filter(factionID -> factionID.startsWith("keleres") && !"keleres".equals(factionID))
                        .map(Mapper::getFaction)
                        .toList();

                    // guess subfaction based on homeplanet
                    for (FactionModel factionModel : keleresSubfactions) {

                        // Check if a keleres home system is on the board.
                        Tile homesystem = game.getTile(factionModel.getHomeSystem());
                        if (homesystem == null) {
                            homesystem = game.getTile(factionModel.getHomeSystem().replace("new", ""));
                        }
                        if (homesystem != null) {
                            factionSetupInfo = Mapper.getFaction(factionModel.getAlias());
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
                            Tile homeSystem = game.getTileFromPlanet(firstHomePlanet.get().toLowerCase());
                            if (homeSystem != null) {
                                boolean isHomeSystemUsedBySomeoneElse = false;
                                for (String factionId : game.getFactions()) {
                                    FactionModel otherFactionSetup = Mapper.getFaction(factionId);
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
                    .filter(TechnologyModel::isUnitUpgrade)
                    .toList();

                for (TechnologyModel technologyModel : playerTechs) {
                    UnitModel upgradedUnit = Mapper.getUnitModelByTechUpgrade(technologyModel.getAlias());
                    if (upgradedUnit != null) {
                        if (StringUtils.isNotBlank(upgradedUnit.getBaseType())) {
                            int upgradesFromUnitIndex = ownedUnitIDs.indexOf(upgradedUnit.getBaseType());
                            if (upgradesFromUnitIndex >= 0) {
                                ownedUnitIDs.set(upgradesFromUnitIndex, upgradedUnit.getId());
                            } else {
                                ownedUnitIDs.add(upgradedUnit.getId());
                            }
                        } else {
                            ownedUnitIDs.add(upgradedUnit.getId());
                        }
                    }
                }
                Set<String> updatedUnitIDs = new HashSet<>(ownedUnitIDs);
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
                    List<FactionModel> keleresSubfactions = Mapper.getFactionIDs().stream()
                        .filter(factionID -> factionID.startsWith("keleres") && !"keleres".equals(factionID))
                        .map(Mapper::getFaction)
                        .toList();

                    // guess subfaction based on homeplanet
                    for (FactionModel factionModel : keleresSubfactions) {

                        // Check if a keleres home system is on the board.
                        Tile homesystem = game.getTile(factionModel.getHomeSystem());
                        if (homesystem == null) {
                            homesystem = game.getTile(factionModel.getHomeSystem().replace("new", ""));
                        }
                        if (homesystem != null) {
                            factionSetupInfo = Mapper.getFaction(factionModel.getAlias());
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
                            Tile homeSystem = game.getTileFromPlanet(firstHomePlanet.get().toLowerCase());
                            if (homeSystem != null) {
                                boolean isHomeSystemUsedBySomeoneElse = false;
                                for (String factionId : game.getFactions()) {
                                    FactionModel otherFactionSetup = Mapper.getFaction(factionId);
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
                    .filter(TechnologyModel::isUnitUpgrade)
                    .toList();

                for (TechnologyModel technologyModel : playerTechs) {
                    UnitModel upgradedUnit = Mapper.getUnitModelByTechUpgrade(technologyModel.getAlias());
                    if (upgradedUnit != null) {
                        if (upgradedUnit.getUpgradesFromUnitId().isPresent()) {
                            int upgradesFromUnitIndex = ownedUnitIDs.indexOf(upgradedUnit.getUpgradesFromUnitId().get());
                            if (upgradesFromUnitIndex >= 0) {
                                ownedUnitIDs.set(upgradesFromUnitIndex, upgradedUnit.getId());
                            }
                        } else {
                            ownedUnitIDs.add(upgradedUnit.getId());
                        }
                    }
                }
                Set<String> updatedUnitIDs = new HashSet<>(ownedUnitIDs);
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

    private static Boolean migrateNaaluMechsToOmega_180923(Game game) {
        boolean mapNeededMigrating = false;
        for (Player player : game.getPlayers().values()) {
            if (player.hasUnit("naalu_mech")) {
                player.removeOwnedUnitByID("naalu_mech");
                player.addOwnedUnitByID("naalu_mech_omega");
                mapNeededMigrating = true;
            }
        }
        return mapNeededMigrating;
    }

    private static Boolean migrateGheminaAddCarrier_190923(Game game) {
        boolean mapNeededMigrating = false;
        for (Player player : game.getPlayers().values()) {
            if ("ghemina".equalsIgnoreCase(player.getFaction()) && player.hasUnit("carrier")) {
                player.removeOwnedUnitByID("carrier");
                player.addOwnedUnitByID("ghemina_carrier");
                mapNeededMigrating = true;
            }
            if ("ghemina".equalsIgnoreCase(player.getFaction()) && player.hasUnit("carrier2")) {
                player.removeOwnedUnitByID("carrier2");
                player.addOwnedUnitByID("ghemina_carrier2");
                mapNeededMigrating = true;
            }
        }
        return mapNeededMigrating;
    }

    private static Boolean migrateRenameVeldyrAttachments_270923(Game game) {
        boolean mapNeededMigrating = false;
        for (Entry<String, Planet> entry : game.getPlanetsInfo().entrySet()) {
            Planet p = entry.getValue();
            Set<String> tokens = new HashSet<>(p.getTokenList());
            for (String token : tokens) {
                if ("attachment_veldyr1.png".equals(token)) {
                    p.removeToken(token);
                    p.addToken("attachment_veldyrtaxhaven.png");
                    mapNeededMigrating = true;
                } else if ("attachment_veldyr2.png".equals(token)) {
                    p.removeToken(token);
                    p.addToken("attachment_veldyrbroadcasthub.png");
                    mapNeededMigrating = true;
                } else if ("attachment_veldyr3.png".equals(token)) {
                    p.removeToken(token);
                    p.addToken("attachment_veldyrreservebank.png");
                    mapNeededMigrating = true;
                } else if ("attachment_veldyr4.png".equals(token)) {
                    p.removeToken(token);
                    p.addToken("attachment_veldyrorbitalshipyard.png");
                    mapNeededMigrating = true;
                }
            }
        }
        return mapNeededMigrating;
    }

    private static Boolean migrateRenameDSExplores_061023(Game game) {
        boolean mapNeededMigrating = false;
        if ("explores_DS".equals(game.getExplorationDeckID())) {
            List<String> oldDeck = game.getAllExplores();
            List<String> replacementDeck = new ArrayList<>();
            for (String ex : oldDeck) {
                String newEx = ex.replace("_", "");
                replacementDeck.add(newEx);
            }
            if (!replacementDeck.equals(oldDeck)) {
                game.setExploreDeck(new ArrayList<>(replacementDeck));
                mapNeededMigrating = true;
            }

            List<String> oldDiscard = game.getAllExploreDiscard();
            List<String> replacementDiscard = new ArrayList<>();
            for (String ex : oldDiscard) {
                String newEx = ex.replace("_", "");
                replacementDiscard.add(newEx);
            }
            if (!replacementDiscard.equals(oldDiscard)) {
                game.setExploreDiscard(new ArrayList<>(replacementDiscard));
                mapNeededMigrating = true;
            }
        }
        return mapNeededMigrating;
    }

    // June 14th, 2024
    public static boolean resetMinorFactionCommanders_130624(Game game) {
        if (!game.isMinorFactionsMode()) return false;
        boolean anyFound = false;
        game.setStoredValue("fakeCommanders", "");
        for (Tile t : game.getTileMap().values()) {
            if (t.isHomeSystem()) {
                String planet = t.getPlanetUnitHolders().isEmpty() ? null : t.getPlanetUnitHolders().getFirst().getName();
                String faction = planet == null ? null : Mapper.getPlanet(planet).getFactionHomeworld();
                if (faction != null && game.getPlayerFromColorOrFaction(faction) == null) {
                    anyFound = true;
                    List<String> commanders = Mapper.getFaction(faction).getLeaders().stream()
                        .filter(leader -> Mapper.getLeader(leader).getType().equals("commander")).toList();
                    commanders.forEach(game::addFakeCommander);
                }
            }
        }
        return anyFound;
    }

    // June 29th, 2024
    public static boolean removeBadCVToken_290624(Game game) {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("token_custodiavigilia_1.png", "attachment_custodiavigilia_1.png");
        tokens.put("token_custodiavigilia_2.png", "attachment_custodiavigilia_2.png");
        return replaceTokens(game, tokens);
    }

    public static boolean noMoreRiftset_311024(Game game) {
        Player rift = game.getPlayerFromColorOrFaction("ero");
        if (rift == null) return false;
        if (rift.getUserID().equals(Constants.eronousId)) return false;

        String newColor = rift.getNextAvailableColorIgnoreCurrent();
        if (game.getPlayerFromColorOrFaction(newColor) != null) return false;
        String oldColor = rift.getColor();
        MessageHelper.sendMessageToChannel(game.getTableTalkChannel(), rift.getRepresentation(false, false) + " has had their color changed to " + Emojis.getColorEmojiWithName(newColor));
        ChangeColor.changePlayerColor(game, rift, oldColor, newColor);
        return true;
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
        Map<String, Game> loadedMaps = GameManager.getGameNameToGame();
        for (Game game : loadedMaps.values()) {
            DateFormat mapCreatedOnFormat = new SimpleDateFormat("yyyy.MM.dd");
            Date mapCreatedOn = null;
            try {
                mapCreatedOn = mapCreatedOnFormat.parse(game.getCreationDate());
            } catch (ParseException ignored) {
            }
            if (mapCreatedOn == null || mapCreatedOn.after(migrationForGamesBeforeDate)) {
                continue;
            }
            boolean endVPReachedButNotEnded = game.getPlayers().values().stream().anyMatch(player -> player.getTotalVictoryPoints() >= game.getVp());
            if (game.isHasEnded() || endVPReachedButNotEnded) {
                continue;
            }

            if (!game.hasRunMigration(migrationName)) {
                Boolean changesMade = migrationMethod.apply(game);
                game.addMigration(migrationName);

                if (changesMade) {
                    migrationsAppliedThisTime.add(game.getName());
                    GameSaveLoadManager.saveGame(game, "Data Migration - " + migrationName);
                }
            }
        }
        if (!migrationsAppliedThisTime.isEmpty()) {
            String mapNames = String.join(", ", migrationsAppliedThisTime);
            BotLogger.log(String.format("Migration %s run on following maps successfully: \n%s", migrationName, mapNames));
        }
    }

    public static boolean migrateFrankenItems_111223(Game game) {
        if (game.getActiveBagDraft() == null) {
            return false;
        }

        for (Player p : game.getRealPlayers()) {
            replaceFrankenItemsInBag_111223(p.getDraftHand());
            replaceFrankenItemsInBag_111223(p.getCurrentDraftBag());
            replaceFrankenItemsInBag_111223(p.getDraftQueue());
        }

        return true;
    }

    private static void replaceFrankenItemsInBag_111223(DraftBag bag) {
        for (int i = 0; i < bag.Contents.size(); i++) {
            DraftItem item = bag.Contents.get(i);
            if ("keleres".equals(item.ItemId)) {
                var newItem = DraftItem.generate(item.ItemCategory, "keleresa");
                swapBagItem(bag, i, newItem);
                item = newItem;
            }
            if (item.ItemCategory == DraftItem.Category.MECH) {
                if (Mapper.getUnit(item.ItemId) == null) {
                    var faction = Mapper.getFaction(item.ItemId);
                    var units = faction.getUnits();
                    units.removeIf((String unit) -> !"mech".equals(Mapper.getUnit(unit).getBaseType()));
                    swapBagItem(bag, i, DraftItem.generate(DraftItem.Category.MECH, units.getFirst()));
                }
            } else if (item.ItemCategory == DraftItem.Category.FLAGSHIP) {
                if (Mapper.getUnit(item.ItemId) == null) {
                    var faction = Mapper.getFaction(item.ItemId);
                    var units = faction.getUnits();
                    units.removeIf((String unit) -> !"flagship".equals(Mapper.getUnit(unit).getBaseType()));
                    swapBagItem(bag, i, DraftItem.generate(DraftItem.Category.FLAGSHIP, units.getFirst()));
                }
            } else if (item.ItemCategory == DraftItem.Category.AGENT) {
                if (Mapper.getLeader(item.ItemId) == null) {
                    var faction = Mapper.getFaction(item.ItemId);
                    List<String> agents = faction.getLeaders();
                    agents.removeIf((String leader) -> !"agent".equals(Mapper.getLeader(leader).getType()));
                    swapBagItem(bag, i, DraftItem.generate(DraftItem.Category.AGENT, agents.getFirst()));
                }
            } else if (item.ItemCategory == DraftItem.Category.COMMANDER) {
                if (Mapper.getLeader(item.ItemId) == null) {
                    var faction = Mapper.getFaction(item.ItemId);
                    List<String> agents = faction.getLeaders();
                    agents.removeIf((String leader) -> !"commander".equals(Mapper.getLeader(leader).getType()));
                    swapBagItem(bag, i, DraftItem.generate(DraftItem.Category.COMMANDER, agents.getFirst()));
                }
            } else if (item.ItemCategory == DraftItem.Category.HERO) {
                if (Mapper.getLeader(item.ItemId) == null) {
                    var faction = Mapper.getFaction(item.ItemId);
                    List<String> agents = faction.getLeaders();
                    agents.removeIf((String leader) -> !"hero".equals(Mapper.getLeader(leader).getType()));
                    swapBagItem(bag, i, DraftItem.generate(DraftItem.Category.HERO, agents.getFirst()));
                }
            } else if (item.ItemCategory == DraftItem.Category.PN) {
                if (Mapper.getPromissoryNote(item.ItemId) == null) {
                    var faction = Mapper.getFaction(item.ItemId);
                    List<String> pns = faction.getPromissoryNotes();
                    swapBagItem(bag, i, DraftItem.generate(DraftItem.Category.PN, pns.getFirst()));
                }
            }
        }
    }

    private static void swapBagItem(DraftBag bag, int index, DraftItem newItem) {
        BotLogger.log(String.format("Draft Bag replacing %s with %s", bag.Contents.get(index).getAlias(), newItem.getAlias()));
        bag.Contents.remove(index);
        bag.Contents.add(index, newItem);
    }

    private static boolean replaceTokens(Game game, Map<String, String> replacements) {
        boolean found = false;
        for (Tile t : game.getTileMap().values()) {
            for (UnitHolder uh : t.getUnitHolders().values()) {
                Set<String> oldList = new HashSet<>(uh.getTokenList());
                for (Entry<String, String> entry : replacements.entrySet()) {
                    if (oldList.contains(entry.getKey())) {
                        uh.removeToken(entry.getKey());
                        uh.addToken(entry.getValue());
                        found = true;
                    }
                }
            }
        }
        return found;
    }

    @SuppressWarnings("unused")
    private static boolean replaceStage1s(Game game, List<String> decksToCheck, Map<String, String> replacements) {
        if (!decksToCheck.contains(game.getStage1PublicDeckID())) {
            return false;
        }

        boolean mapNeededMigrating = false;
        for (String toReplace : replacements.keySet()) {
            String replacement = replacements.get(toReplace);

            mapNeededMigrating |= replace(game.getPublicObjectives1(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getRevealedPublicObjectives(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getScoredPublicObjectives(), toReplace, replacement);
        }
        return mapNeededMigrating;
    }

    @SuppressWarnings("unused")
    private static boolean replaceActionCards(Game game, List<String> decksToCheck, Map<String, String> replacements) {
        if (!decksToCheck.contains(game.getAcDeckID())) {
            return false;
        }

        boolean mapNeededMigrating = false;
        for (String toReplace : replacements.keySet()) {
            String replacement = replacements.get(toReplace);

            mapNeededMigrating |= replace(game.getActionCards(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getDiscardActionCards(), toReplace, replacement);

            for (Player player : game.getRealPlayers()) {
                mapNeededMigrating |= replaceKey(player.getActionCards(), toReplace, replacement);
            }
        }
        return mapNeededMigrating;
    }

    @SuppressWarnings("unused")
    private static boolean replaceAgendaCards(Game game, List<String> decksToCheck, Map<String, String> replacements) {
        if (!decksToCheck.contains(game.getAgendaDeckID())) {
            return false;
        }

        boolean mapNeededMigrating = false;
        for (String toReplace : replacements.keySet()) {
            String replacement = replacements.get(toReplace);

            mapNeededMigrating |= replace(game.getAgendas(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getDiscardAgendas(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getSentAgendas(), toReplace, replacement);
        }
        return mapNeededMigrating;
    }

    private static <K, V> boolean replaceKey(Map<K, V> map, K toReplace, K replacement) {
        if (map.containsKey(toReplace)) {
            V value = map.get(toReplace);
            map.put(replacement, value);
            map.remove(toReplace);
            return true;
        }
        return false;
    }

    private static <K> boolean replace(List<K> list, K toReplace, K replacement) {
        int index = list.indexOf(toReplace);
        if (index > -1) {
            list.set(index, replacement);
            return true;
        }
        return false;
    }
}
