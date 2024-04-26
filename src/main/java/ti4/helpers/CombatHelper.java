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
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
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
            getUnitsOnHolderByAsyncId(colorID, unitsByAsyncId, unitHolder);
        }

        Map<UnitModel, Integer> unitsInCombat = getUnitsInCombat(player, unitsByAsyncId);

        HashMap<UnitModel, Integer> output = new HashMap<>(unitsInCombat.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().getAfbDieCount() > 0)
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
        checkBadUnits(player, event, unitsByAsyncId, output);

        return output;
    }

    private static Map<UnitModel, Integer> getUnitsInCombat(Player player, Map<String, Integer> unitsByAsyncId) {
        return unitsByAsyncId.entrySet().stream().map(
            entry -> new ImmutablePair<>(
                player.getPriorityUnitByAsyncID(entry.getKey(), null),
                entry.getValue()))
            .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private static void getUnitsOnHolderByAsyncId(String colorID, Map<String, Integer> unitsByAsyncId, UnitHolder unitHolder) {
        Map<String, Integer> unitsOnHolderByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        for (Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {
            Integer existingCount = 0;
            if (unitsByAsyncId.containsKey(unitEntry.getKey())) {
                existingCount = unitsByAsyncId.get(unitEntry.getKey());
            }
            unitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
        }
    }

    public static Map<UnitModel, Integer> GetUnitsInBombardment(Tile tile, Player player,
        GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            getUnitsOnHolderByAsyncId(colorID, unitsByAsyncId, unitHolder);
        }
        Map<UnitModel, Integer> unitsInCombat = getUnitsInCombat(player, unitsByAsyncId);

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
        if (player.getPlanetsAllianceMode().contains("mr") && "mr".equalsIgnoreCase(planet.getName()) && player.hasTech("iihq")) {
            PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
            planet.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
            planet.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
        }
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
            getUnitsOnHolderByAsyncId(colorID, unitsByAsyncId, unitHolder);
        }

        Map<String, Integer> adjacentUnitsByAsyncId = new HashMap<>();
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false);
        for (String adjacentTilePosition : adjTiles) {
            if (adjacentTilePosition.equals(tile.getPosition())) {
                continue;
            }
            Tile adjTile = activeGame.getTileByPosition(adjacentTilePosition);
            for (UnitHolder unitHolder : adjTile.getUnitHolders().values()) {
                getUnitsOnHolderByAsyncId(colorID, adjacentUnitsByAsyncId, unitHolder);
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
                if (player.getPlanetsAllianceMode().contains("mr") && "mr".equalsIgnoreCase(planet.getName()) && player.hasTech("iihq")) {
                    PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
                    planet.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
                    planet.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
                }
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
        if (player.hasAbility("starfall_gunnery")) {
            if (player == activeGame.getActivePlayer()) {
                int count = Math.min(3, ButtonHelper.checkNumberNonFighterShipsWithoutSpaceCannon(player, activeGame, tile));
                if (count > 0) {
                    UnitModel starfallFakeUnit = new UnitModel();
                    starfallFakeUnit.setSpaceCannonHitsOn(8);
                    starfallFakeUnit.setSpaceCannonDieCount(1);
                    starfallFakeUnit
                        .setName("Starfall Gunnery space cannon");
                    starfallFakeUnit.setAsyncId("starfallpds");
                    starfallFakeUnit.setId("starfallpds");
                    starfallFakeUnit.setBaseType("pds");
                    starfallFakeUnit.setFaction(player.getFaction());
                    unitsOnTile.put(starfallFakeUnit, count);
                }
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmoji() + " this is a reminder that due to the starfall gunnery ability, only space cannon of 1 unit should be counted at this point. Hopefully you declared beforehand what that unit was, but by default its probably the best one. Only look at/count the rolls of that one unit");
            }
        }

        HashMap<UnitModel, Integer> output = new HashMap<>(unitsOnTile.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().getSpaceCannonDieCount(player, activeGame) > 0)
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));

        HashMap<UnitModel, Integer> adjacentOutput = new HashMap<>(unitsOnAdjacentTiles.entrySet().stream()
            .filter(entry -> entry.getKey() != null
                && entry.getKey().getSpaceCannonDieCount(player, activeGame) > 0
                && (entry.getKey().getDeepSpaceCannon() || activeGame.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander") || (entry.getKey().getBaseType().equalsIgnoreCase("spacedock"))))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
        int limit = 0;
        for (var entry : adjacentOutput.entrySet()) {
            if (entry.getKey().getDeepSpaceCannon()) {
                if (output.containsKey(entry.getKey())) {
                    output.put(entry.getKey(), entry.getValue() + output.get(entry.getKey()));
                } else {
                    output.put(entry.getKey(), entry.getValue());
                }
            } else {
                if (limit < 1) {
                    limit = 1;
                    if (output.containsKey(entry.getKey())) {
                        output.put(entry.getKey(), 1 + output.get(entry.getKey()));
                    } else {
                        output.put(entry.getKey(), 1);
                    }
                }
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

        if (dupes.size() > 0) {
            CombatMessageHelper.displayDuplicateUnits(event, missing);
        }
        if (missing.size() > 0) {
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
                .filter(opp -> opp.getUserID().equals(activeGame.getActivePlayerID()))
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
        Game activeGame, CombatRollType rollType, GenericInteractionCreateEvent event, Tile activeSystem) {

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
        int totalMisses = 0;
        UnitHolder space = activeSystem.getUnitHolders().get("space");
        String extra = "";
        for (Map.Entry<UnitModel, Integer> entry : playerUnits.entrySet()) {
            UnitModel unit = entry.getKey();
            int numOfUnit = entry.getValue();

            int toHit = unit.getCombatDieHitsOnForAbility(rollType, player, activeGame);
            int modifierToHit = CombatModHelper.GetCombinedModifierForUnit(unit, numOfUnit, mods, player, opponent,
                activeGame,
                playerUnitsList, opponentUnitsList, rollType, activeSystem);
            int extraRollsForUnit = CombatModHelper.GetCombinedModifierForUnit(unit, numOfUnit, extraRolls, player,
                opponent,
                activeGame, playerUnitsList, opponentUnitsList, rollType, activeSystem);
            int numRollsPerUnit = unit.getCombatDieCountForAbility(rollType, player, activeGame);
            boolean extraRollsCount = false;
            if ((numRollsPerUnit > 1 || extraRollsForUnit > 0) && activeGame.getStoredValue("thalnosPlusOne").equalsIgnoreCase("true")) {
                extraRollsCount = true;
                numRollsPerUnit = 1;
                extraRollsForUnit = 0;
            }
            if (rollType == CombatRollType.SpaceCannonOffence && numRollsPerUnit == 3 && unit.getBaseType().equalsIgnoreCase("spacedock")) {
                numOfUnit = 1;
                activeGame.setStoredValue("EBSFaction", "");
            }
            int numRolls = (numOfUnit * numRollsPerUnit) + extraRollsForUnit;
            List<Die> resultRolls = DiceHelper.rollDice(toHit - modifierToHit, numRolls);
            player.setExpectedHitsTimes10(player.getExpectedHitsTimes10() + (numRolls * (11 - toHit + modifierToHit)));

            int hitRolls = DiceHelper.countSuccesses(resultRolls);
            if (unit.getId().equalsIgnoreCase("jolnar_flagship")) {
                for (Die die : resultRolls) {
                    if (die.getResult() > 8) {
                        hitRolls = hitRolls + 2;
                    }
                }
            }
            int misses = numRolls - hitRolls;
            totalMisses = totalMisses + misses;

            if (misses > 0 && !extraRollsCount && activeGame.getStoredValue("thalnosPlusOne").equalsIgnoreCase("true")) {
                extra = player.getFactionEmoji() + " destroyed " + misses + " of their own " + unit.getName() + " due to Thalnos misses";
                for (String thalnosUnit : activeGame.getThalnosUnits().keySet()) {
                    String pos = thalnosUnit.split("_")[0];
                    String unitHolderName = thalnosUnit.split("_")[1];
                    Tile tile = activeGame.getTileByPosition(pos);
                    int amount = activeGame.getSpecificThalnosUnit(thalnosUnit);
                    String unitName = ButtonHelper.getUnitName(unit.getAsyncId());
                    thalnosUnit = thalnosUnit.split("_")[2].replace("damaged", "");
                    if (thalnosUnit.equals(unitName)) {
                        new RemoveUnits().unitParsing(event, player.getColor(), tile, misses + " " + unitName + " " + unitHolderName, activeGame);
                        if (unitName.equalsIgnoreCase("infantry")) {
                            ButtonHelper.resolveInfantryDeath(activeGame, player, misses);
                        }
                        if (unitName.equalsIgnoreCase("mech")) {
                            if (player.hasUnit("mykomentori_mech")) {
                                for (int x = 0; x < misses; x++) {
                                    ButtonHelper.rollMykoMechRevival(activeGame, player);
                                }
                            }
                            if (player.hasTech("sar")) {
                                for (int x = 0; x < misses; x++) {
                                    player.setTg(player.getTg() + 1);
                                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + " you gained 1tg (" + (player.getTg() - 1)
                                        + "->" + player.getTg() + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain.");
                                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                                }
                                ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                            }
                        }
                        break;
                    }
                }

            } else {
                if (misses > 0 && activeGame.getStoredValue("thalnosPlusOne").equalsIgnoreCase("true")) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmoji() + " had " + misses + " " + unit.getName() + " misses on a thalnos roll, but no units were removed due to extra rolls being unaccounted for");
                }
            }

            totalHits += hitRolls;

            String unitRoll = CombatMessageHelper.displayUnitRoll(unit, toHit, modifierToHit, numOfUnit, numRollsPerUnit, extraRollsForUnit, resultRolls, hitRolls);
            resultBuilder.append(unitRoll);
            List<Die> resultRolls2 = new ArrayList<>();
            int numMisses = numRolls - hitRolls;
            if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "jolnarcommander") && rollType != CombatRollType.combatround && numMisses > 0) {
                int numRolls2 = numMisses;
                resultRolls2 = DiceHelper.rollDice(toHit - modifierToHit, numRolls2);
                player.setExpectedHitsTimes10(player.getExpectedHitsTimes10() + (numRolls2 * (11 - toHit + modifierToHit)));
                int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                totalHits += hitRolls2;
                String unitRoll2 = CombatMessageHelper.displayUnitRoll(unit, toHit, modifierToHit, numOfUnit, numRollsPerUnit, 0, resultRolls2, hitRolls2);
                resultBuilder.append("Rerolling " + numMisses + " misses due to Jol-Nar Commander:\n " + unitRoll2);
            }

            if (activeGame.getStoredValue("munitionsReserves").equalsIgnoreCase(player.getFaction()) && rollType == CombatRollType.combatround && numMisses > 0) {
                int numRolls2 = numMisses;
                resultRolls2 = DiceHelper.rollDice(toHit - modifierToHit, numRolls2);
                player.setExpectedHitsTimes10(player.getExpectedHitsTimes10() + (numRolls2 * (11 - toHit + modifierToHit)));
                int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                totalHits += hitRolls2;
                String unitRoll2 = CombatMessageHelper.displayUnitRoll(unit, toHit, modifierToHit, numOfUnit, numRollsPerUnit, 0, resultRolls2, hitRolls2);
                resultBuilder.append("Munitions rerolling " + numMisses + " misses: " + unitRoll2);
            }

            int argentInfKills = 0;
            if (player != opponent && unit.getId().equalsIgnoreCase("argent_destroyer2") && rollType == CombatRollType.AFB && space.getUnitCount(UnitType.Infantry, opponent.getColor()) > 0) {
                for (Die die : resultRolls) {
                    if (die.getResult() > 8) {
                        argentInfKills++;
                    }
                }
                for (Die die : resultRolls2) {
                    if (die.getResult() > 8) {
                        argentInfKills++;
                    }
                }
                argentInfKills = Math.min(argentInfKills, space.getUnitCount(UnitType.Infantry, opponent.getColor()));
            }
            if (argentInfKills > 0) {
                String kills = "\nDue to SWA II destroyer ability, " + argentInfKills + " of " + opponent.getRepresentation(false, true) + " infantry were destroyed\n";
                resultBuilder.append(kills);
                space.removeUnit(Mapper.getUnitKey(AliasHandler.resolveUnit("infantry"), opponent.getColorID()), argentInfKills);
                ButtonHelper.resolveInfantryDeath(activeGame, opponent, argentInfKills);
            }
        }
        result = resultBuilder.toString();

        result += CombatMessageHelper.displayHitResults(totalHits);
        player.setActualHits(player.getActualHits() + totalHits);
        if (player.hasRelic("thalnos") && rollType == CombatRollType.combatround && totalMisses > 0 && !activeGame.getStoredValue("thalnosPlusOne").equalsIgnoreCase("true")) {
            result = result + "\n" + player.getFactionEmoji() + " You have crown of thalnos and can reroll misses (adding +1) at the risk of your troops lives";
        }
        if (totalHits > 0 && CombatRollType.bombardment == rollType && player.hasTech("dszelir")) {
            result = result + "\n" + player.getFactionEmoji() + " You have shard volley and thus should produce an additional hit to the ones rolled above";
        }
        if (!extra.isEmpty()) {
            result = result + "\n\n" + extra;
        }
        if (activeGame.getStoredValue("munitionsReserves").equalsIgnoreCase(player.getFaction()) && rollType == CombatRollType.combatround) {
            activeGame.setStoredValue("munitionsReserves", "");
        }
        return result;
    }
}