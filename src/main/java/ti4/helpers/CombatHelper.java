package ti4.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.model.NamedCombatModifierModel;
import ti4.model.PlanetModel;
import ti4.model.UnitModel;

public class CombatHelper {

    public static Map<UnitModel, Integer> GetAllUnits(UnitHolder unitHolder, Player player) {
        String colorID = Mapper.getColorID(player.getColor());
        Map<String, Integer> unitsByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        Map<UnitModel, Integer> unitsInCombat = unitsByAsyncId.entrySet().stream().flatMap(
                entry -> player.getUnitsByAsyncID(entry.getKey()).stream()
                        .map(x -> new ImmutablePair<>(x, entry.getValue())))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        HashMap<UnitModel, Integer> output;

        output = new HashMap<>(unitsInCombat.entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));

        Set<String> duplicates = new HashSet<>();
        List<String> dupes = output.keySet().stream()
                .filter(unit -> !duplicates.add(unit.getAsyncId()))
                .map(UnitModel::getBaseType)
                .toList();
        for (String dupe : dupes) {
            for (UnitModel mod : output.keySet()) {
                if (mod.getBaseType().equalsIgnoreCase(dupe) && !mod.getId().contains("2")) {
                    output.put(mod, 0);
                    break;
                }
            }
        }
        return output;
    }

    public static Map<UnitModel, Integer> GetUnitsInCombat(Tile tile, UnitHolder unitHolder, Player player,
            GenericInteractionCreateEvent event, CombatRollType roleType, Game activeGame) {
        Planet unitHolderPlanet = null;
        if (unitHolder instanceof Planet) {
            unitHolderPlanet = (Planet) unitHolder;
        }
      return switch (roleType) {
        case combatround -> GetUnitsInCombatRound(unitHolder, player, event, tile);
        case AFB -> GetUnitsInAFB(tile, player, event);
        case bombardment -> GetUnitsInBombardment(tile, player, event);
        case SpaceCannonOffence -> getUnitsInSpaceCannonOffense(tile, player, event, activeGame);
        case SpaceCannonDefence -> getUnitsInSpaceCannonDefence(unitHolderPlanet, player, event);
      };
    }

    public static Map<UnitModel, Integer> GetUnitsInCombatRound(UnitHolder unitHolder, Player player,
            GenericInteractionCreateEvent event, Tile tile) {
        String colorID = Mapper.getColorID(player.getColor());
        Map<String, Integer> unitsByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        Map<UnitModel, Integer> unitsInCombat = unitsByAsyncId.entrySet().stream().map(
                entry -> new ImmutablePair<>(
                        player.getPriorityUnitByAsyncID(entry.getKey(), unitHolder),
                        entry.getValue()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
        HashMap<UnitModel, Integer> output;
        if (unitHolder.getName().equals(Constants.SPACE)) {
            if (unitsByAsyncId.containsKey("fs") && player.hasUnit("nekro_flagship")) {
                output = new HashMap<>(unitsInCombat.entrySet().stream()
                        .filter(entry -> entry.getKey() != null
                                && (entry.getKey().getIsGroundForce() || entry.getKey().getIsShip()))
                        .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
                for (UnitHolder u2 : tile.getUnitHolders().values()) {
                    if (u2 == unitHolder) {
                        continue;
                    }
                    Map<String, Integer> unitsByAsyncId2 = u2.getUnitAsyncIdsOnHolder(colorID);
                    Map<UnitModel, Integer> unitsInCombat2 = unitsByAsyncId2.entrySet().stream().map(
                            entry -> new ImmutablePair<>(
                                    player.getPriorityUnitByAsyncID(entry.getKey(), unitHolder),
                                    entry.getValue()))
                            .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
                    HashMap<UnitModel, Integer> output2;
                    output2 = new HashMap<>(unitsInCombat2.entrySet().stream()
                            .filter(entry -> entry.getKey() != null
                                    && (entry.getKey().getIsGroundForce() || entry.getKey().getIsShip()))
                            .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
                    for (UnitModel unit : output2.keySet()) {
                        if (output.containsKey(unit)) {
                            output.put(unit, output.get(unit) + output2.get(unit));
                        } else {
                            output.put(unit, output2.get(unit));
                        }
                    }
                }
            } else {
                output = new HashMap<>(unitsInCombat.entrySet().stream()
                        .filter(entry -> entry.getKey() != null && entry.getKey().getIsShip())
                        .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
            }
        } else {
            output = new HashMap<>(unitsInCombat.entrySet().stream()
                    .filter(entry -> entry.getKey() != null
                            && (entry.getKey().getIsGroundForce() || entry.getKey().getIsShip()))
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
        }
        checkBadUnits(player, event, unitsByAsyncId, output);

        return output;
    }

    public static Map<UnitModel, Integer> GetUnitsInAFB(Tile tile, Player player,
            GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());

        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            Map<String, Integer> unitsOnHolderByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
            for (Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {
                Integer existingCount = 0;
                if (unitsByAsyncId.containsKey(unitEntry.getKey())) {
                    existingCount = unitsByAsyncId.get(unitEntry.getKey());
                }
                unitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
            }
        }

        Map<UnitModel, Integer> unitsInCombat = unitsByAsyncId.entrySet().stream().map(
                entry -> new ImmutablePair<>(
                        player.getPriorityUnitByAsyncID(entry.getKey(), null),
                        entry.getValue()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        HashMap<UnitModel, Integer> output = new HashMap<>(unitsInCombat.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().getAfbDieCount() > 0)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
        checkBadUnits(player, event, unitsByAsyncId, output);

        return output;
    }

    public static Map<UnitModel, Integer> GetUnitsInBombardment(Tile tile, Player player,
            GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            Map<String, Integer> unitsOnHolderByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
            for (Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {
                Integer existingCount = 0;
                if (unitsByAsyncId.containsKey(unitEntry.getKey())) {
                    existingCount = unitsByAsyncId.get(unitEntry.getKey());
                }
                unitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
            }
        }
        Map<UnitModel, Integer> unitsInCombat = unitsByAsyncId.entrySet().stream().map(
                entry -> new ImmutablePair<>(
                        player.getPriorityUnitByAsyncID(entry.getKey(), null),
                        entry.getValue()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        HashMap<UnitModel, Integer> output = new HashMap<>(unitsInCombat.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().getBombardDieCount() > 0)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
        checkBadUnits(player, event, unitsByAsyncId, output);

        return output;
    }

    public static Map<UnitModel, Integer> getUnitsInSpaceCannonDefence(Planet planet, Player player,
            GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());

        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        if (planet == null) {
            return new HashMap<>();
        }

        Map<String, Integer> unitsOnHolderByAsyncId = planet.getUnitAsyncIdsOnHolder(colorID);
        for (Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {
            Integer existingCount = 0;
            if (unitsByAsyncId.containsKey(unitEntry.getKey())) {
                existingCount = unitsByAsyncId.get(unitEntry.getKey());
            }
            unitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
        }

        Map<UnitModel, Integer> unitsOnPlanet = unitsByAsyncId.entrySet().stream().map(
                entry -> new ImmutablePair<>(
                        player.getPriorityUnitByAsyncID(entry.getKey(), null),
                        entry.getValue()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        // Check for space cannon die on planet
        PlanetModel planetModel = Mapper.getPlanet(planet.getName());
        String ccID = Mapper.getControlID(player.getColor());
        if (planet.getControlList().contains(ccID) && planet.getSpaceCannonDieCount() > 0) {
            UnitModel planetFakeUnit = new UnitModel();
            planetFakeUnit.setSpaceCannonHitsOn(planet.getSpaceCannonHitsOn());
            planetFakeUnit.setSpaceCannonDieCount(planet.getSpaceCannonDieCount());
            planetFakeUnit.setName(Helper.getPlanetRepresentationPlusEmoji(planetModel.getId()) + " space cannon");
            planetFakeUnit.setAsyncId(planet.getName() + "pds");
            planetFakeUnit.setId(planet.getName() + "pds");
            planetFakeUnit.setBaseType("pds");
            planetFakeUnit.setFaction(player.getFaction());
            unitsOnPlanet.put(planetFakeUnit, 1);
        }

        HashMap<UnitModel, Integer> output = new HashMap<>(unitsOnPlanet.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().getSpaceCannonDieCount() > 0)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));

        checkBadUnits(player, event, unitsByAsyncId, output);

        return output;
    }

    public static Map<UnitModel, Integer> getUnitsInSpaceCannonOffense(Tile tile, Player player,
            GenericInteractionCreateEvent event, Game activeGame) {
        String colorID = Mapper.getColorID(player.getColor());

        Map<String, Integer> unitsByAsyncId = new HashMap<>();

        Collection<UnitHolder> unitHolders = tile.getUnitHolders().values();
        for (UnitHolder unitHolder : unitHolders) {
            Map<String, Integer> unitsOnHolderByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
            for (Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {
                Integer existingCount = 0;
                if (unitsByAsyncId.containsKey(unitEntry.getKey())) {
                    existingCount = unitsByAsyncId.get(unitEntry.getKey());
                }
                unitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
            }
        }

        Map<String, Integer> adjacentUnitsByAsyncId = new HashMap<>();
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false);
        for (String adjacentTilePosition : adjTiles) {
            if (adjacentTilePosition.equals(tile.getPosition())) {
                continue;
            }
            Tile adjTile = activeGame.getTileByPosition(adjacentTilePosition);
            for (UnitHolder unitHolder : adjTile.getUnitHolders().values()) {
                Map<String, Integer> unitsOnHolderByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
                for (Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {
                    Integer existingCount = 0;
                    if (adjacentUnitsByAsyncId.containsKey(unitEntry.getKey())) {
                        existingCount = adjacentUnitsByAsyncId.get(unitEntry.getKey());
                    }
                    adjacentUnitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
                }
            }
        }

        Map<UnitModel, Integer> unitsOnTile = unitsByAsyncId.entrySet().stream().map(
                entry -> new ImmutablePair<>(
                        player.getPriorityUnitByAsyncID(entry.getKey(), null),
                        entry.getValue()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
        Map<UnitModel, Integer> unitsOnAdjacentTiles = adjacentUnitsByAsyncId.entrySet().stream().map(
                entry -> new ImmutablePair<>(
                        player.getPriorityUnitByAsyncID(entry.getKey(), null),
                        entry.getValue()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        // Check for space cannon die on planets
        for (UnitHolder unitHolder : unitHolders) {
            if (unitHolder instanceof Planet planet) {
                PlanetModel planetModel = Mapper.getPlanet(planet.getName());
                String ccID = Mapper.getControlID(player.getColor());
                if (planet.getControlList().contains(ccID) && planet.getSpaceCannonDieCount() > 0) {
                    UnitModel planetFakeUnit = new UnitModel();
                    planetFakeUnit.setSpaceCannonHitsOn(planet.getSpaceCannonHitsOn());
                    planetFakeUnit.setSpaceCannonDieCount(planet.getSpaceCannonDieCount());
                    planetFakeUnit
                            .setName(Helper.getPlanetRepresentationPlusEmoji(planetModel.getId()) + " space cannon");
                    planetFakeUnit.setAsyncId(planet.getName() + "pds");
                    planetFakeUnit.setId(planet.getName() + "pds");
                    planetFakeUnit.setBaseType("pds");
                    planetFakeUnit.setFaction(player.getFaction());
                    unitsOnTile.put(planetFakeUnit, 1);
                }
            }
        }

        HashMap<UnitModel, Integer> output = new HashMap<>(unitsOnTile.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().getSpaceCannonDieCount() > 0)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));

        HashMap<UnitModel, Integer> adjacentOutput = new HashMap<>(unitsOnAdjacentTiles.entrySet().stream()
                .filter(entry -> entry.getKey() != null
                        && entry.getKey().getSpaceCannonDieCount() > 0
                        && entry.getKey().getDeepSpaceCannon())
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));

        for (var entry : adjacentOutput.entrySet()) {
            if (output.containsKey(entry.getKey())) {
                output.put(entry.getKey(), entry.getValue() + output.get(entry.getKey()));
            } else {
                output.put(entry.getKey(), entry.getValue());
            }
        }

        checkBadUnits(player, event, unitsByAsyncId, output);

        return output;
    }

    private static void checkBadUnits(Player player, GenericInteractionCreateEvent event,
            Map<String, Integer> unitsByAsyncId, HashMap<UnitModel, Integer> output) {
        Set<String> duplicates = new HashSet<>();
        List<String> dupes = output.keySet().stream()
                .filter(unit -> !duplicates.add(unit.getAsyncId()))
                .map(UnitModel::getBaseType)
                .collect(Collectors.toList());
        List<String> missing = unitsByAsyncId.keySet().stream()
                .filter(unit -> player.getUnitsByAsyncID(unit.toLowerCase()).isEmpty())
                .collect(Collectors.toList());

        if(dupes.size() > 0){
            CombatMessageHelper.displayDuplicateUnits(event, missing);
        }
        if(missing.size() > 0){
            CombatMessageHelper.displayMissingUnits(event, missing);
        }
    }

    public static Player GetOpponent(Player player, List<UnitHolder> unitHolders, Game activeGame) {
        Player opponent = null;
        String playerColorID = Mapper.getColorID(player.getColor());
        List<Player> opponents = unitHolders.stream().flatMap(holder -> holder.getUnitColorsOnHolder().stream())
                .filter(color -> !color.equals(playerColorID))
                .map(activeGame::getPlayerByColorID)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        if (opponents.size() >= 1) {
            opponent = opponents.get(0);
        }
        if (opponents.size() > 1) {
            Optional<Player> activeOpponent = opponents.stream()
                    .filter(opp -> opp.getUserID().equals(activeGame.getActivePlayer()))
                    .findAny();
            if (activeOpponent.isPresent()) {
                opponent = activeOpponent.get();
            }
        }
        return opponent;
    }

    public static String RollForUnits(Map<UnitModel, Integer> playerUnits, Map<UnitModel, Integer> opponentUnits,
            List<NamedCombatModifierModel> extraRolls, 
            List<NamedCombatModifierModel> autoMods, List<NamedCombatModifierModel> tempMods, Player player,
            Player opponent,
            Game activeGame, CombatRollType rollType) {
        String result = "";

        List<NamedCombatModifierModel> mods = new ArrayList<>(autoMods);
        mods.addAll(tempMods);

        List<NamedCombatModifierModel> modAndExtraRolls = new ArrayList<>(mods);
        modAndExtraRolls.addAll(extraRolls);

        result += CombatMessageHelper.displayModifiers("With modifiers: \n", playerUnits, modAndExtraRolls);

        // Actually roll for each unit
        int totalHits = 0;
        StringBuilder resultBuilder = new StringBuilder(result);
        List<UnitModel> playerUnitsList = new ArrayList<>(playerUnits.keySet());
        List<UnitModel> opponentUnitsList = new ArrayList<>(opponentUnits.keySet());
        for (Map.Entry<UnitModel, Integer> entry : playerUnits.entrySet()) {
            UnitModel unit = entry.getKey();
            int numOfUnit = entry.getValue();

            int toHit = unit.getCombatDieHitsOnForAbility(rollType);
            int modifierToHit = CombatModHelper.GetCombinedModifierForUnit(unit, numOfUnit, mods, player, opponent,
                    activeGame,
                    playerUnitsList, opponentUnitsList, rollType);
            int extraRollsForUnit = CombatModHelper.GetCombinedModifierForUnit(unit, numOfUnit, extraRolls, player,
                    opponent,
                    activeGame, playerUnitsList, opponentUnitsList, rollType);
            int numRollsPerUnit = unit.getCombatDieCountForAbility(rollType);

            int numRolls = (numOfUnit * numRollsPerUnit) + extraRollsForUnit;
            List<Die> resultRolls = DiceHelper.rollDice(toHit - modifierToHit, numRolls);
            player.setExpectedHitsTimes10(player.getExpectedHitsTimes10() + (numRolls * (11 - toHit + modifierToHit)));

            int hitRolls = DiceHelper.countSuccesses(resultRolls);
            totalHits += hitRolls;

            String unitRoll = CombatMessageHelper.displayUnitRoll(unit, toHit, modifierToHit, numOfUnit, numRollsPerUnit, extraRollsForUnit, resultRolls, hitRolls);
            resultBuilder.append(unitRoll);
        }
        result = resultBuilder.toString();

        result += CombatMessageHelper.displayHitResults(totalHits);
        player.setActualHits(player.getActualHits() + totalHits);
        return result;
    }
}