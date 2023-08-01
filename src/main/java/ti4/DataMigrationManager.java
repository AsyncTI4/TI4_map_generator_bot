package ti4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.model.FactionModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

public class DataMigrationManager {

    public static void runMigrations() {
        HashMap<String, List<String>> migrationsAppliedToMaps = new HashMap<>();
        HashMap<String, Map> loadedMaps = MapManager.getInstance().getMapList();

        migrationsAppliedToMaps.put("migrateFixkeleresUnits_010823", new ArrayList<String>());
        migrationsAppliedToMaps.put("migrateOwnedUnits_010823", new ArrayList<String>());
        for (Map map : loadedMaps.values()) {
            Boolean endVPReachedButNotEnded = map.getPlayers().values().stream()
                    .anyMatch(player -> player.getTotalVictoryPoints(map) >= map.getVp());
            if (map.isHasEnded() || endVPReachedButNotEnded) {
                continue;
            }

            if (!map.hasRunMigration("migrateFixkeleresUnits_010823")) {
                migrateFixkeleresUnits_010823(map);
                map.addMigration("migrateFixkeleresUnits_010823");

                migrationsAppliedToMaps.get("migrateFixkeleresUnits_010823").add(map.getName());
            }

            if (!map.hasRunMigration("migrateOwnedUnits_010823")) {
                migrateOwnedUnits_010823(map);
                map.addMigration("migrateOwnedUnits_010823");

                migrationsAppliedToMaps.get("migrateOwnedUnits_010823").add(map.getName());
            }
        }

        if (migrationsAppliedToMaps.size() > 0) {

            for (Entry<String, List<String>> entry : migrationsAppliedToMaps.entrySet()) {
                String mapNames = String.join(", ", entry.getValue());
                if (entry.getValue().size() > 0) {
                    BotLogger.log(
                            String.format("Migration %s run on following maps successfully: %s", entry.getKey(),
                                    mapNames));
                }
            }
        }
    }

    /// MIGRATION: Refresh owned units
    /// There was a bug recently in Player.doAdditionalThingsWhenAddingTech
    /// That didnt allow for unit upgrades to be included when there wasnt a pre-req (eg war sun)
    /// Additionally, there are historical issues of unit upgrades not being included in units_owned
    /// This migration looks to each players' faction setup for its base units
    /// Then looks to the player.techs for any upgrades to the base units to get an uptodate list of owned units. 
    ///
    public static void migrateOwnedUnits_010823(Map map) {
        try {
            for (Player player : map.getRealPlayers()) {
                FactionModel factionSetupInfo = player.getFactionSetupInfo();

                // Trying to assign an accurate Faction Model for old keleres players.
                if (factionSetupInfo == null && player.getFaction().equals("keleres")) {
                    List<FactionModel> keleresSubfactions = Mapper.getFactions().stream()
                            .filter(factionID -> factionID.startsWith("keleres") && !factionID.equals("keleres"))
                            .map(factionID -> Mapper.getFactionSetup(factionID))
                            .collect(Collectors.toList());

                    // guess subfaction based on homeplanet
                    for (FactionModel factionModel : keleresSubfactions) {

                        // Check if a keleres home system is on the board.
                        Tile homesystem = map.getTile(factionModel.getHomeSystem());
                        if (homesystem == null) {
                            homesystem = map.getTile(factionModel.getHomeSystem().replace("new", ""));
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
                                .collect(Collectors.toList());

                        Optional<String> firstHomePlanet = subfactionHomePlanetsWithoutKeleresSuffix.stream()
                                .findFirst();
                        if (firstHomePlanet.isPresent()) {
                            Tile homeSystem = Helper.getTileFromPlanet(firstHomePlanet.get().toLowerCase(), map);
                            if (homeSystem != null) {
                                Boolean isHomeSystemUsedBySomeoneElse = false;
                                for (String factionId : map.getFactions()) {
                                    FactionModel otherFactionSetup = Mapper.getFactionSetup(factionId);
                                    if (otherFactionSetup.getHomeSystem().equals(homeSystem.getTileID())) {
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
                                    player.getFaction(), map.getName()));
                }
                List<String> ownedUnitIDs = factionSetupInfo.getUnits();

                List<TechnologyModel> playerTechs = player.getTechs().stream().map(techID -> Mapper.getTech(techID))
                        .filter(tech -> tech.getType().equals(Constants.UNIT_UPGRADE))
                        .collect(Collectors.toList());

                for (TechnologyModel technologyModel : playerTechs) {
                    UnitModel upgradedUnit = Mapper.getUnitModelByTechUpgrade(technologyModel.getAlias());
                    if (upgradedUnit != null) {
                        if (StringUtils.isNotBlank(upgradedUnit.getUpgradesFromUnitId())) {
                            Integer upgradesFromUnitIndex = ownedUnitIDs.indexOf(upgradedUnit.getUpgradesFromUnitId());
                            if (upgradesFromUnitIndex != null && upgradesFromUnitIndex >= 0) {
                                ownedUnitIDs.set(upgradesFromUnitIndex, upgradedUnit.getId());
                            }
                        } else {
                            ownedUnitIDs.add(upgradedUnit.getId());
                        }
                    }
                }
                player.setUnitsOwned(new HashSet<String>(ownedUnitIDs));
            }
        } catch (Exception e) {
            BotLogger.log("Failed to migrate owned units for map" + map.getName(), e);
        }

    }

    /// MIGRATION: Fix Keleres units 
    /// We've updated faction_setup.json so that the keleres factions all share the
    /// same unit type
    /// and this migration checks for any suffixed keleres units already existing
    /// incorrectly in games.
    public static void migrateFixkeleresUnits_010823(Map map) {

        for (Player player : map.getRealPlayers()) {

            List<String> ownedUnitIDs = new ArrayList<>(player.getUnitsOwned());
            for (String unitID : ownedUnitIDs) {
                Integer unitIndex = ownedUnitIDs.indexOf(unitID);
                if (unitID.startsWith("keleres") && unitID.endsWith("_flagship")) {
                    ownedUnitIDs.set(unitIndex, "keleres_flagship");
                }
                if (unitID.startsWith("keleres") && unitID.endsWith("_mech")) {
                    ownedUnitIDs.set(unitIndex, "keleres_mech");
                }
            }

            player.setUnitsOwned(new HashSet<String>(ownedUnitIDs));
        }
    }
}
