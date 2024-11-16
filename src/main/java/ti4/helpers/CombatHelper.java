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

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import ti4.buttons.Buttons;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.tokens.AddCC;
import ti4.generator.Mapper;
import ti4.generator.TileGenerator;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
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
        GenericInteractionCreateEvent event, CombatRollType roleType, Game game) {
        Planet unitHolderPlanet = null;
        if (unitHolder instanceof Planet) {
            unitHolderPlanet = (Planet) unitHolder;
        }
        return switch (roleType) {
            case combatround -> GetUnitsInCombatRound(unitHolder, player, event, tile);
            case AFB -> GetUnitsInAFB(tile, player, event);
            case bombardment -> GetUnitsInBombardment(tile, player, event);
            case SpaceCannonOffence -> getUnitsInSpaceCannonOffense(tile, player, event, game);
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
            .filter(entry -> entry.getKey() != null && entry.getKey().getAfbDieCount(player, player.getGame()) > 0)
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
            .filter(entry -> entry.getKey() != null && entry.getKey().getBombardDieCount(player, player.getGame()) > 0)
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
        if (player.controlsMecatol(true) && Constants.MECATOLS.contains(planet.getName()) && player.hasTech("iihq")) {
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
        GenericInteractionCreateEvent event, Game game) {
        String colorID = Mapper.getColorID(player.getColor());

        Map<String, Integer> unitsByAsyncId = new HashMap<>();

        Collection<UnitHolder> unitHolders = tile.getUnitHolders().values();
        for (UnitHolder unitHolder : unitHolders) {
            getUnitsOnHolderByAsyncId(colorID, unitsByAsyncId, unitHolder);
        }

        Map<String, Integer> adjacentUnitsByAsyncId = new HashMap<>();
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false);
        for (String adjacentTilePosition : adjTiles) {
            if (adjacentTilePosition.equals(tile.getPosition())) {
                continue;
            }
            Tile adjTile = game.getTileByPosition(adjacentTilePosition);
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
                if (player.controlsMecatol(true) && Constants.MECATOLS.contains(planet.getName()) && player.hasTech("iihq")) {
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
            if (player == game.getActivePlayer()) {
                int count = Math.min(3, ButtonHelper.checkNumberNonFighterShipsWithoutSpaceCannon(player, game, tile));
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
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmoji() + " this is a reminder that due to the Starfall Gunnery ability, only Space Cannon of 1 unit should be counted at this point. Hopefully you declared beforehand what that unit was, but by default it's probably the best one. Only look at/count the rolls of that one unit");
            }
        }

        HashMap<UnitModel, Integer> output = new HashMap<>(unitsOnTile.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().getSpaceCannonDieCount(player, game) > 0)
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));

        HashMap<UnitModel, Integer> adjacentOutput = new HashMap<>(unitsOnAdjacentTiles.entrySet().stream()
            .filter(entry -> entry.getKey() != null
                && entry.getKey().getSpaceCannonDieCount(player, game) > 0
                && (entry.getKey().getDeepSpaceCannon() || game.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander") || (entry.getKey().getBaseType().equalsIgnoreCase("spacedock"))))
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
            .toList();
        List<String> missing = unitsByAsyncId.keySet().stream()
            .filter(unit -> player.getUnitsByAsyncID(unit.toLowerCase()).isEmpty())
            .collect(Collectors.toList());

        if (!dupes.isEmpty()) {
            CombatMessageHelper.displayDuplicateUnits(event, missing);
        }
        if (!missing.isEmpty()) {
            CombatMessageHelper.displayMissingUnits(event, missing);
        }
    }

    public static Player GetOpponent(Player player, List<UnitHolder> unitHolders, Game game) {
        Player opponent = null;
        String playerColorID = Mapper.getColorID(player.getColor());
        List<Player> opponents = unitHolders.stream().flatMap(holder -> holder.getUnitColorsOnHolder().stream())
            .filter(color -> !color.equals(playerColorID))
            .map(game::getPlayerByColorID)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        if (!opponents.isEmpty()) {
            opponent = opponents.getFirst();
        }
        if (opponents.size() > 1) {
            Optional<Player> activeOpponent = opponents.stream()
                .filter(opp -> opp.getUserID().equals(game.getActivePlayerID()))
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
        Game game, CombatRollType rollType, GenericInteractionCreateEvent event, Tile activeSystem) {

        String result = "";

        List<NamedCombatModifierModel> mods = new ArrayList<>(autoMods);
        mods.addAll(tempMods);
        Set<NamedCombatModifierModel> set2 = new HashSet<>(mods);
        mods = new ArrayList<>(set2);

        List<NamedCombatModifierModel> modAndExtraRolls = new ArrayList<>(mods);
        modAndExtraRolls.addAll(extraRolls);
        Set<NamedCombatModifierModel> set = new HashSet<>(modAndExtraRolls);
        List<NamedCombatModifierModel> uniqueList = new ArrayList<>(set);
        result += CombatMessageHelper.displayModifiers("With modifiers: \n", playerUnits, uniqueList);

        // Actually roll for each unit
        int totalHits = 0;
        StringBuilder resultBuilder = new StringBuilder(result);
        List<UnitModel> playerUnitsList = new ArrayList<>(playerUnits.keySet());
        List<UnitModel> opponentUnitsList = new ArrayList<>(opponentUnits.keySet());
        int totalMisses = 0;
        UnitHolder space = activeSystem.getUnitHolders().get("space");
        StringBuilder extra = new StringBuilder();
        for (Map.Entry<UnitModel, Integer> entry : playerUnits.entrySet()) {
            UnitModel unitModel = entry.getKey();
            int numOfUnit = entry.getValue();

            int toHit = unitModel.getCombatDieHitsOnForAbility(rollType, player, game);
            int modifierToHit = CombatModHelper.GetCombinedModifierForUnit(unitModel, numOfUnit, mods, player, opponent,
                game,
                playerUnitsList, opponentUnitsList, rollType, activeSystem);
            int extraRollsForUnit = CombatModHelper.GetCombinedModifierForUnit(unitModel, numOfUnit, extraRolls, player,
                opponent,
                game, playerUnitsList, opponentUnitsList, rollType, activeSystem);
            int numRollsPerUnit = unitModel.getCombatDieCountForAbility(rollType, player, game);
            boolean extraRollsCount = false;
            if ((numRollsPerUnit > 1 || extraRollsForUnit > 0) && game.getStoredValue("thalnosPlusOne").equalsIgnoreCase("true")) {
                extraRollsCount = true;
                numRollsPerUnit = 1;
                extraRollsForUnit = 0;
            }
            if (rollType == CombatRollType.SpaceCannonOffence && numRollsPerUnit == 3 && unitModel.getBaseType().equalsIgnoreCase("spacedock")) {
                numOfUnit = 1;
                game.setStoredValue("EBSFaction", "");
            }
            if (rollType == CombatRollType.bombardment && numRollsPerUnit > 1 && unitModel.getBaseType().equalsIgnoreCase("destroyer")) {
                numOfUnit = 1;
                game.setStoredValue("TnelisAgentFaction", "");
            }
            int numRolls = (numOfUnit * numRollsPerUnit) + extraRollsForUnit;
            List<Die> resultRolls = DiceHelper.rollDice(toHit - modifierToHit, numRolls);
            player.setExpectedHitsTimes10(player.getExpectedHitsTimes10() + (numRolls * (11 - toHit + modifierToHit)));

            int hitRolls = DiceHelper.countSuccesses(resultRolls);
            if (unitModel.getId().equalsIgnoreCase("jolnar_flagship")) {
                for (Die die : resultRolls) {
                    if (die.getResult() > 8) {
                        hitRolls = hitRolls + 2;
                    }
                }
            }
            if (rollType == CombatRollType.combatround && (player.hasAbility("valor") || opponent.hasAbility("valor")) && ButtonHelperAgents.getGloryTokenTiles(game).contains(activeSystem)) {
                for (Die die : resultRolls) {
                    if (die.getResult() > 9) {
                        hitRolls = hitRolls + 1;
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " got an extra hit due to the valor ability (it has been accounted for in the hit count).");
                    }
                }
            }
            if (unitModel.getId().equalsIgnoreCase("vaden_flagship") && CombatRollType.bombardment == rollType) {
                for (Die die : resultRolls) {
                    if (die.getResult() > 4) {
                        player.setTg(player.getTg() + 1);
                        ButtonHelperAbilities.pillageCheck(player, game);
                        ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " gained 1TG due to hitting on a bombardment roll with the Aurum Vadra (the Vaden flagship).");
                        break;

                    }
                }
            }
            int misses = numRolls - hitRolls;
            totalMisses = totalMisses + misses;

            if (misses > 0 && !extraRollsCount && game.getStoredValue("thalnosPlusOne").equalsIgnoreCase("true")) {
                extra.append(player.getFactionEmoji()).append(" destroyed ").append(misses).append(" of their own ").append(unitModel.getName()).append(misses == 1 ? "" : "s").append(" due to ").append(misses == 1 ? "a Thalnos miss" : "Thalnos misses");
                for (String thalnosUnit : game.getThalnosUnits().keySet()) {
                    String pos = thalnosUnit.split("_")[0];
                    String unitHolderName = thalnosUnit.split("_")[1];
                    Tile tile = game.getTileByPosition(pos);
                    //int amount = game.getSpecificThalnosUnit(thalnosUnit);
                    String unitName = unitModel.getAsyncId();
                    thalnosUnit = thalnosUnit.split("_")[2].replace("damaged", "");
                    if (thalnosUnit.equals(unitName)) {
                        UnitModifier.unitParsing(event, player.getColor(), tile, misses + " " + unitName + " " + unitHolderName, game);
                        if (unitName.equalsIgnoreCase("infantry")) {
                            ButtonHelper.resolveInfantryDeath(game, player, misses);
                        }
                        if (unitName.equalsIgnoreCase("mech")) {
                            if (player.hasUnit("mykomentori_mech")) {
                                for (int x = 0; x < misses; x++) {
                                    ButtonHelper.rollMykoMechRevival(game, player);
                                }
                            }
                            if (player.hasTech("sar")) {
                                for (int x = 0; x < misses; x++) {
                                    player.setTg(player.getTg() + 1);
                                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " you gained 1TG (" + (player.getTg() - 1)
                                        + "->" + player.getTg() + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain.");
                                    ButtonHelperAbilities.pillageCheck(player, game);
                                }
                                ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                            }
                        }
                        break;
                    }
                }

            } else {
                if (misses > 0 && game.getStoredValue("thalnosPlusOne").equalsIgnoreCase("true")) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        player.getFactionEmoji() + " had " + misses + " " + unitModel.getName() + (misses == 1 ? "" : "s") + " miss" + (misses == 1 ? "" : "es")
                            + " on a Thalnos roll, but no units were removed due to extra rolls being unaccounted for.");
                }
            }

            totalHits += hitRolls;

            String unitRoll = CombatMessageHelper.displayUnitRoll(unitModel, toHit, modifierToHit, numOfUnit, numRollsPerUnit, extraRollsForUnit, resultRolls, hitRolls);
            resultBuilder.append(unitRoll);
            List<Die> resultRolls2 = new ArrayList<>();
            int numMisses = numRolls - hitRolls;
            if (game.playerHasLeaderUnlockedOrAlliance(player, "jolnarcommander") && rollType != CombatRollType.combatround && numMisses > 0) {
                int numRolls2 = numMisses;
                resultRolls2 = DiceHelper.rollDice(toHit - modifierToHit, numRolls2);
                player.setExpectedHitsTimes10(player.getExpectedHitsTimes10() + (numRolls2 * (11 - toHit + modifierToHit)));
                int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                totalHits += hitRolls2;
                String unitRoll2 = CombatMessageHelper.displayUnitRoll(unitModel, toHit, modifierToHit, numOfUnit, numRollsPerUnit, 0, resultRolls2, hitRolls2);
                resultBuilder.append("Rerolling ").append(numMisses).append(" miss").append(numMisses == 1 ? "" : "es").append(" due to Ta Zern, the Jol-Nar Commander:\n ").append(unitRoll2);
            }

            if (game.getStoredValue("munitionsReserves").equalsIgnoreCase(player.getFaction()) && rollType == CombatRollType.combatround && numMisses > 0) {
                int numRolls2 = numMisses;
                resultRolls2 = DiceHelper.rollDice(toHit - modifierToHit, numRolls2);
                player.setExpectedHitsTimes10(player.getExpectedHitsTimes10() + (numRolls2 * (11 - toHit + modifierToHit)));
                int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                totalHits += hitRolls2;
                String unitRoll2 = CombatMessageHelper.displayUnitRoll(unitModel, toHit, modifierToHit, numOfUnit, numRollsPerUnit, 0, resultRolls2, hitRolls2);
                resultBuilder.append("Munitions rerolling ").append(numMisses).append(" miss").append(numMisses == 1 ? "" : "es").append(": ").append(unitRoll2);
            }

            int argentInfKills = 0;
            if (player != opponent && unitModel.getId().equalsIgnoreCase("argent_destroyer2") && rollType == CombatRollType.AFB && space.getUnitCount(UnitType.Infantry, opponent.getColor()) > 0) {
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
                ButtonHelper.resolveInfantryDeath(game, opponent, argentInfKills);
            }
        }
        result = resultBuilder.toString();

        result += CombatMessageHelper.displayHitResults(totalHits);
        player.setActualHits(player.getActualHits() + totalHits);
        if (player.hasRelic("thalnos") && rollType == CombatRollType.combatround && totalMisses > 0 && !game.getStoredValue("thalnosPlusOne").equalsIgnoreCase("true")) {
            result = result + "\n" + player.getFactionEmoji() + " You have the Crown of Thalnos and may reroll " + (totalMisses == 1 ? "the miss" : "misses")
                + ", adding +1, at the risk of your " + (totalMisses == 1 ? "troop's life" : "troops' lives") + ".";
        }
        if (totalHits > 0 && CombatRollType.bombardment == rollType && player.hasTech("dszelir")) {
            result = result + "\n" + player.getFactionEmoji() + " You have Shard Volley and thus should produce an additional hit to the ones rolled above.";
        }
        if (!extra.isEmpty()) {
            result = result + "\n\n" + extra;
        }
        if (game.getStoredValue("munitionsReserves").equalsIgnoreCase(player.getFaction()) && rollType == CombatRollType.combatround) {
            game.setStoredValue("munitionsReserves", "");
        }
        return result;
    }

    @ButtonHandler("automateGroundCombat_")
    public static void automateGroundCombat(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String faction1 = buttonID.split("_")[1];
        String faction2 = buttonID.split("_")[2];
        Player p1 = game.getPlayerFromColorOrFaction(faction1);
        Player p2 = game.getPlayerFromColorOrFaction(faction2);
        Player opponent = null;
        String planet = buttonID.split("_")[3];
        String confirmed = buttonID.split("_")[4];
        if (player != p1 && player != p2) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This button is only for combat participants");
            return;
        }
        if (player == p2) {
            opponent = p1;
        } else {
            opponent = p2;
        }
        ButtonHelper.deleteTheOneButton(event);
        if (opponent == null || opponent.isDummy() || confirmed.equalsIgnoreCase("confirmed")) {
            ButtonHelperModifyUnits.autoMateGroundCombat(p1, p2, planet, game, event);
        } else if (p1 != null && p2 != null) {
            Button automate = Buttons.green(opponent.getFinsFactionCheckerPrefix() + "automateGroundCombat_"
                + p1.getFaction() + "_" + p2.getFaction() + "_" + planet + "_confirmed", "Automate Combat");
            MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), opponent.getRepresentation() + " Your opponent has voted to automate the entire combat. Press to confirm:", automate);
        }
    }

    public static void startGroundCombat(Player player, Player player2, Game game, GenericInteractionCreateEvent event, UnitHolder unitHolder, Tile tile) {
        String threadName = combatThreadName(game, player, player2, tile);
        if (!game.isFowMode()) {
            findOrCreateCombatThread(game, game.getActionsChannel(), player, player2,
                    threadName, tile, event, "ground", unitHolder.getName());
            if ((unitHolder.getUnitCount(UnitType.Pds, player2.getColor()) < 1
                    || (!player2.hasUnit("titans_pds") && !player2.hasUnit("titans_pds2")))
                    && unitHolder.getUnitCount(UnitType.Mech, player2.getColor()) < 1
                    && unitHolder.getUnitCount(UnitType.Infantry, player2.getColor()) < 1
                    && (unitHolder.getUnitCount(UnitType.Pds, player2.getColor()) > 0
                    || unitHolder.getUnitCount(UnitType.Spacedock, player2.getColor()) > 0)) {
                String msg2 = player2.getRepresentation()
                        + " you may want to remove structures on " + unitHolder.getName() + " if your opponent is not playing Infiltrate or using Assimilate. Use buttons to resolve.";
                List<Button> buttons = new ArrayList<>();
                buttons.add(
                        Buttons.red(player2.getFinsFactionCheckerPrefix() + "removeAllStructures_" + unitHolder.getName(),
                                "Remove Structures"));
                buttons.add(Buttons.gray("deleteButtons", "Don't remove Structures"));
                MessageHelper.sendMessageToChannel(player2.getCorrectChannel(), msg2, buttons);
            }
        } else {
            findOrCreateCombatThread(game, player.getPrivateChannel(), player, player2,
                    threadName, tile, event, "ground", unitHolder.getName());
            if (player2.isRealPlayer()) {
                findOrCreateCombatThread(game, player2.getPrivateChannel(), player2, player,
                        threadName, tile, event, "ground", unitHolder.getName());
            }
            for (Player player3 : game.getRealPlayers()) {
                if (player3 == player2 || player3 == player) {
                    continue;
                }
                if (!tile.getRepresentationForButtons(game, player3).contains("(")) {
                    continue;
                }
                createSpectatorThread(game, player3, threadName, tile, event, "ground");
            }
        }
    }

    public static String combatThreadName(Game game, Player player1, @Nullable Player player2, Tile tile) {
        StringBuilder sb = new StringBuilder();
        sb.append(game.getName()).append("-round-").append(game.getRound()).append("-system-")
                .append(tile.getPosition()).append("-turn-").append(player1.getTurnCount()).append("-");
        if (game.isFowMode()) {
            sb.append(player1.getColor());
            if (player2 != null)
                sb.append("-vs-").append(player2.getColor()).append("-private");
        } else {
            sb.append(player1.getFaction());
            if (player2 != null)
                sb.append("-vs-").append(player2.getFaction());
        }
        return sb.toString();
    }

    public static void findOrCreateCombatThread(Game game, MessageChannel channel, Player player1, Player player2,
                                                Tile tile, GenericInteractionCreateEvent event, String spaceOrGround, String unitHolderName) {
        findOrCreateCombatThread(game, channel, player1, player2, null, tile, event, spaceOrGround, unitHolderName);
    }

    public static void startSpaceCombat(Game game, Player player, Player player2, Tile tile, GenericInteractionCreateEvent event) {
        startSpaceCombat(game, player, player2, tile, event, null);
    }

    private static String combatThreadName(Game game, Player player1, @Nullable Player player2, Tile tile, String specialCombatTitle) {
        StringBuilder sb = new StringBuilder();
        sb.append(game.getName()).append("-round-").append(game.getRound()).append("-system-")
                .append(tile.getPosition()).append("-turn-").append(player1.getTurnCount()).append("-");
        if (game.isFowMode()) {
            sb.append(player1.getColor());
            if (player2 != null) {
                sb.append("-vs-").append(player2.getColor());
            }
            sb.append(specialCombatTitle != null ? specialCombatTitle : "");
            sb.append("-private");
        } else {
            sb.append(player1.getFaction());
            if (player2 != null) {
                sb.append("-vs-").append(player2.getFaction());
            }
            sb.append(specialCombatTitle != null ? specialCombatTitle : "");
        }
        return sb.toString();
    }

    public static void startSpaceCombat(Game game, Player player, Player player2, Tile tile, GenericInteractionCreateEvent event, String specialCombatTitle) {
        String threadName = combatThreadName(game, player, player2, tile, specialCombatTitle);
        if (!game.isFowMode()) {
            findOrCreateCombatThread(game, game.getActionsChannel(), player, player2,
                    threadName, tile, event, "space", "space");
        } else {
            findOrCreateCombatThread(game, player.getPrivateChannel(), player, player2,
                    threadName, tile, event, "space", "space");
            if (player2.getPrivateChannel() != null) {
                findOrCreateCombatThread(game, player2.getPrivateChannel(), player2, player,
                        threadName, tile, event, "space", "space");
            }
            for (Player player3 : game.getRealPlayers()) {
                if (player3 == player2 || player3 == player) {
                    continue;
                }
                if (!tile.getRepresentationForButtons(game, player3).contains("(")) {
                    continue;
                }
                createSpectatorThread(game, player3, threadName, tile, event, "space");
            }
        }
    }

    public static void findOrCreateCombatThread(Game game, MessageChannel channel, Player player1, Player player2, String threadName, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround, String unitHolderName) {
        Helper.checkThreadLimitAndArchive(event.getGuild());
        if (threadName == null)
            threadName = combatThreadName(game, player1, player2, tile);
        if (!game.isFowMode()) {
            channel = game.getMainGameChannel();
        }
        game.setStoredValue("factionsInCombat", player1.getFaction() + "_" + player2.getFaction());

        sendStartOfCombatSecretMessages(game, player1, player2, tile, spaceOrGround, unitHolderName);
        String combatName2 = "combatRoundTracker" + player1.getFaction() + tile.getPosition() + unitHolderName;
        game.setStoredValue(combatName2, "");
        combatName2 = "combatRoundTracker" + player2.getFaction() + tile.getPosition() + unitHolderName;
        game.setStoredValue(combatName2, "");

        TextChannel textChannel = (TextChannel) channel;
        // Use existing thread, if it exists
        for (ThreadChannel threadChannel_ : textChannel.getThreadChannels()) {
            if (threadChannel_.getName().equals(threadName)) {
                initializeCombatThread(threadChannel_, game, player1, player2, tile, event, spaceOrGround, null, unitHolderName);
                return;
            }
        }
        if (tile.isMecatol()) {
            CommanderUnlockCheck.checkPlayer(player1, "winnu");
            CommanderUnlockCheck.checkPlayer(player2, "winnu");
        }

        int context = getTileImageContextForPDS2(game, player1, tile, spaceOrGround);
        FileUpload systemWithContext = new TileGenerator(game, event, null, context, tile.getPosition()).createFileUpload();

        // Create the thread
        final String finalThreadName = threadName;

        channel.sendMessage("Resolve Combat in this thread:").queue(m -> {
            ThreadChannelAction threadChannel = textChannel.createThreadChannel(finalThreadName, m.getId());
            if (game.isFowMode()) {
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS);
            } else {
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
            }
            threadChannel.queue(tc -> initializeCombatThread(tc, game, player1, player2, tile, event,
                    spaceOrGround, systemWithContext, unitHolderName));
        });
    }

    private static void initializeCombatThread(ThreadChannel threadChannel, Game game, Player player1,
                                               Player player2, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround, FileUpload file, String unitHolderName) {
        StringBuilder message = new StringBuilder();
        message.append(player1.getRepresentationUnfogged());
        if (!game.isFowMode())
            message.append(player2.getRepresentation());

        boolean isSpaceCombat = "space".equalsIgnoreCase(spaceOrGround);
        boolean isGroundCombat = "ground".equalsIgnoreCase(spaceOrGround);

        message.append(" Please resolve the interaction here.\n");
        if (isSpaceCombat)
            message.append(getSpaceCombatIntroMessage());
        if (isGroundCombat)
            message.append(getGroundCombatIntroMessage());

        // PDS2 Context
        int context = getTileImageContextForPDS2(game, player1, tile, spaceOrGround);
        if (file == null) {
            file = new TileGenerator(game, event, null, context, tile.getPosition()).createFileUpload();
        }

        message.append("\nImage of System:");
        MessageHelper.sendMessageWithFile(threadChannel, file, message.toString(), false);

        // Space Cannon Offense
        if (isSpaceCombat) {
            sendSpaceCannonButtonsToThread(threadChannel, game, player1, tile);
        }

        // Start of Space Combat Buttons
        if (isSpaceCombat) {
            sendStartOfSpaceCombatButtonsToThread(threadChannel, game, player1, player2, tile);
        }
        game.setStoredValue("solagent", "");
        game.setStoredValue("letnevagent", "");

        // AFB
        if (isSpaceCombat) {
            sendAFBButtonsToThread(event, threadChannel, game,
                    ButtonHelper.getPlayersWithUnitsInTheSystem(game, tile), tile);
        }

        // General Space Combat
        sendGeneralCombatButtonsToThread(threadChannel, game, player1, player2, tile, spaceOrGround, event);

        if (isGroundCombat && !game.isFowMode()) {
            List<Button> autoButtons = new ArrayList<>();
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                if (!uH.getName().equalsIgnoreCase(unitHolderName)) {
                    continue;
                }
                List<Player> playersWithGF = new ArrayList<>();
                for (Player player : game.getRealPlayersNDummies()) {
                    if (ButtonHelperModifyUnits.doesPlayerHaveGfOnPlanet(uH, player)) {
                        playersWithGF.add(player);
                    }
                }
                if (playersWithGF.size() > 1) {
                    Button automate = Buttons.green("automateGroundCombat_" + playersWithGF.get(0).getFaction() + "_" + playersWithGF.get(1).getFaction() + "_" + unitHolderName + "_unconfirmed", "Automate Combat For " + Helper.getPlanetRepresentation(unitHolderName, game));
                    autoButtons.add(automate);
                }
            }
            if (!autoButtons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(threadChannel, "You may automate the entire combat if neither side has action cards or fancy tricks. Press this button to do so, and it will ask your opponent to confirm. Note that PDS fire and BOMBARDMENT are NOT part of combat and will not be automated.", autoButtons);
            }
        }
        // DS Lanefir ATS Armaments
        if ((player1.hasTech("dslaner") && player1.getAtsCount() > 0) || (player2.hasTech("dslaner") && player2.getAtsCount() > 0)) {
            List<Button> lanefirATSButtons = ButtonHelperFactionSpecific.getLanefirATSButtons(player1, player2);
            MessageHelper.sendMessageToChannelWithButtons(threadChannel, "Buttons to remove commodities from ATS Armaments:", lanefirATSButtons);
        }
    }

    private static void createSpectatorThread(Game game, Player player, String threadName, Tile tile, GenericInteractionCreateEvent event,
                                              String spaceOrGround) {
        Helper.checkThreadLimitAndArchive(event.getGuild());
        FileUpload systemWithContext = new TileGenerator(game, event, null, 0, tile.getPosition()).createFileUpload();

        // Use existing thread, if it exists
        TextChannel textChannel = (TextChannel) player.getPrivateChannel();
        for (ThreadChannel threadChannel_ : textChannel.getThreadChannels()) {
            if (threadChannel_.getName().equals(threadName)) {
                initializeSpectatorThread(threadChannel_, game, player, tile, event, systemWithContext, spaceOrGround);
                return;
            }
        }

        MessageChannel channel = player.getPrivateChannel();
        channel.sendMessage("Spectate Combat in this thread:").queue(m -> {
            ThreadChannelAction threadChannel = ((TextChannel) channel).createThreadChannel(threadName, m.getId());
            threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS);
            threadChannel.queue(tc -> initializeSpectatorThread(tc, game, player, tile, event, systemWithContext, spaceOrGround));
        });
    }

    private static void initializeSpectatorThread(ThreadChannel threadChannel, Game game, Player player,
                                                  Tile tile, GenericInteractionCreateEvent event, FileUpload systemWithContext, String spaceOrGround) {
        StringBuilder message = new StringBuilder();
        message.append(player.getRepresentationUnfogged());
        message.append(" Please spectate the interaction here.\n");
        if ("ground".equals(spaceOrGround)) {
            message.append("## Invasion");
        } else {
            message.append("## Space Combat");
        }
        message.append("\nPlease note, that although you can see the combat participants' messages, you cannot communicate with them.\n");
        message.append("\nImage of System:");
        MessageHelper.sendMessageWithFile(threadChannel, systemWithContext, message.toString(), false);
        sendGeneralCombatButtonsToThread(threadChannel, game, player, player, tile, "justPicture", event);
    }

    public static void sendSpaceCannonButtonsToThread(MessageChannel threadChannel, Game game,
                                                      Player activePlayer, Tile tile) {
        StringBuilder pdsMessage = new StringBuilder();
        if (game.isFowMode()) {
            pdsMessage.append("In fog, it is the Players' responsibility to check for PDS2\n");
        }
        List<Player> playersWithPds2 = ButtonHelper.tileHasPDS2Cover(activePlayer, game, tile.getPosition());
        if (playersWithPds2.isEmpty()) {
            return;
        }
        if (!game.isFowMode() && !playersWithPds2.isEmpty()) {
            pdsMessage.append("These players have space cannon offense coverage in this system:\n");
            for (Player playerWithPds : playersWithPds2) {
                pdsMessage.append("> ").append(playerWithPds.getRepresentation()).append("\n");
            }
        }
        pdsMessage.append("Buttons for Space Cannon Offence:");
        List<Button> spaceCannonButtons = getSpaceCannonButtons(game, activePlayer, tile);
        MessageHelper.sendMessageToChannelWithButtons(threadChannel, pdsMessage.toString(), spaceCannonButtons);
        for (Player player : game.getRealPlayers()) {
            if (ButtonHelper.doesPlayerHaveFSHere("argent_flagship", player, tile)) {
                MessageHelper.sendMessageToChannel(threadChannel, "Reminder that you cannot use space cannon offense against " + player.getFactionEmojiOrColor() + " due to the ability of the Quetzecoatl (the Argent flagship).");
            }
        }
    }

    private static void sendStartOfSpaceCombatButtonsToThread(ThreadChannel threadChannel, Game game,
                                                              Player player1, Player player2, Tile tile) {
        List<Button> startOfSpaceCombatButtons = getStartOfSpaceCombatButtons(game, player1, player2, tile);
        if (!startOfSpaceCombatButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(threadChannel, "Buttons for Start of Space Combat:",
                    startOfSpaceCombatButtons);
        }
    }

    private static void sendStartOfCombatSecretMessages(Game game, Player p1, Player p2, Tile tile, String type, String unitHolderName) {
        List<Player> combatPlayers = new ArrayList<>();
        combatPlayers.add(p1);
        combatPlayers.add(p2);
        List<Button> buttons = new ArrayList<>();

        for (Player player : combatPlayers) {
            Player otherPlayer = p1;
            if (otherPlayer == player) {
                otherPlayer = p2;
            }
            String msg = player.getRepresentation() + " ";
            if (ButtonHelper.doesPlayerHaveFSHere("cymiae_flagship", player, tile)) {
                buttons.add(Buttons.green("resolveSpyStep1", "Resolve Reprocessor Alpha (Cymiae Flagship) Ability"));
                buttons.add(Buttons.red("deleteButtons", "Delete These"));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                                + "if you win the combat, you have the opportunity to use the Reprocessor Alpha (the Cymiae flagship) to force the other player to send you a random action card. It will send buttons to the other player to confirm.",
                        buttons);
            }
            if (type.equalsIgnoreCase("space") && player.getSecretsUnscored().containsKey("uf")
                    && tile.getUnitHolders().get("space").getUnitCount(UnitType.Flagship, player.getColor()) > 0) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                        msg + " this is a reminder that if you win the combat, you may score Unveil Flagship.");
            }
            if (type.equalsIgnoreCase("space") && player.getSecretsUnscored().containsKey("dtgs")
                    && (tile.getUnitHolders().get("space").getUnitCount(UnitType.Flagship, otherPlayer.getColor()) > 0
                    || tile.getUnitHolders().get("space").getUnitCount(UnitType.Warsun,
                    otherPlayer.getColor()) > 0)) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                        + " this is a reminder that you could potentially score Destroy Their Greatest Ship in this combat.");
            }
            if (player.getSecretsUnscored().containsKey("sar")
                    && otherPlayer.getTotalVictoryPoints() == game.getHighestScore()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                        msg + " this is a reminder that you could potentially score Spark a Rebellion in this combat.");
            }
            if (player.getSecretsUnscored().containsKey("btv") && tile.isAnomaly(game)) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                        msg + " this is a reminder that you could potentially score Brave the Void in this combat.");
            }

            if ((player.hasAbility("edict") || player.hasAbility("imperia"))
                    && !player.getMahactCC().contains(otherPlayer.getColor())) {
                buttons = new ArrayList<>();
                String finChecker = "FFCC_" + player.getFaction() + "_";
                buttons.add(Buttons.gray(finChecker + "mahactStealCC_" + otherPlayer.getColor(), "Add Opponent CC to Fleet", Emojis.Mahact));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                                + " this is a reminder that if you win this combat, you may add the opponents CC to your fleet pool.",
                        buttons);
            }
            if (player.hasTechReady("dskortg") && AddCC.hasCC(player, tile)) {
                buttons = new ArrayList<>();
                buttons.add(Buttons.gray("exhaustTech_dskortg_" + tile.getPosition(), "Tempest Drive", Emojis.kortali));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                                + " this is a reminder that if you win the combat, you may use this button to remove a CC from the system.",
                        buttons);
            }
            if (player.hasAbility("technological_singularity")) {
                Button steal = Buttons.gray(player.finChecker() + "nekroStealTech_" + otherPlayer.getFaction(), "Steal Tech", Emojis.Nekro);
                String message = msg + " this is a reminder that when you first kill an opponent unit this combat, you may use the button to steal a tech.";
                MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), message, steal);
            }
            if (player.hasUnit("ghemina_mech") && type.equalsIgnoreCase("ground") && ButtonHelper.getUnitHolderFromPlanetName(unitHolderName, game).getUnitCount(UnitType.Mech, player) == 2) {
                Button explore = Buttons.gray(player.finChecker() + "gheminaMechStart_" + otherPlayer.getFaction(), "Mech Explores", Emojis.ghemina);
                String message = msg + " this is a reminder that if you win the combat, you may use the button to resolve your mech ability.";
                MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), message, explore);
            }

            if (type.equalsIgnoreCase("space") && player.hasTech("so")) {
                buttons = new ArrayList<>();
                buttons.add(Buttons.gray("salvageOps_" + tile.getPosition(), "Salvage Operations", Emojis.Mentak));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                                + " this is a reminder that if the combat does not end in a draw, you may use the button to resolve Salvage Operations.",
                        buttons);
            }
            if (type.equalsIgnoreCase("space")
                    && game.playerHasLeaderUnlockedOrAlliance(player, "mentakcommander")) {
                String finChecker = "FFCC_" + player.getFaction() + "_";
                buttons = new ArrayList<>();
                buttons.add(Buttons.gray(finChecker + "mentakCommander_" + otherPlayer.getColor(), "Resolve Mentak Commander on " + otherPlayer.getColor(), Emojis.Mentak));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg + " this is a reminder that if you win the combat, you may use the button to resolve S'ula Mentarion, the Mentak commander.", buttons);
            }
            if (player.hasAbility("moult") && player != game.getActivePlayer()
                    && "space".equalsIgnoreCase(type)) {
                String finChecker = "FFCC_" + player.getFaction() + "_";
                buttons = new ArrayList<>();
                buttons.add(Buttons.gray(finChecker + "moult_" + tile.getPosition(), "Moult", Emojis.cheiran));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                                + " this is a reminder that if you win the combat, you may use the button to resolve Moult and produce one ship, reducing the cost by 1 for each non-fighter ship you lost in the combat.",
                        buttons);
            }
            if (player.getPromissoryNotes().containsKey("dspnmort")
                    && !player.getPromissoryNotesOwned().contains("dspnmort") && player != game.getActivePlayer()
                    && "space".equalsIgnoreCase(type)) {
                String finChecker = "FFCC_" + player.getFaction() + "_";
                buttons = new ArrayList<>();
                buttons.add(Buttons.gray(finChecker + "startFacsimile_" + tile.getPosition(), "Play Mortheus PN", Emojis.cheiran));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                                + " this is a reminder that you may play Morpheus PN here to spend influence equal to the cost of 1 of the opponent ships to place 1 of that type of ship in the system.",
                        buttons);
            }
            boolean techOrLegendary = false;
            for (UnitHolder planet : tile.getPlanetUnitHolders()) {
                if (ButtonHelper.checkForTechSkips(game, planet.getName())
                        || ButtonHelper.isTileLegendary(tile, game)) {
                    techOrLegendary = true;
                }
            }
            if (techOrLegendary && player.getLeaderIDs().contains("augerscommander")
                    && !player.hasLeaderUnlocked("augerscommander")) {
                buttons = new ArrayList<>();
                buttons.add(Buttons.green("unlockCommander_augers", "Unlock Augurs Commander", Emojis.augers));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                                + " this is a reminder that if you win the combat here, you may use the button to unlock Lachis, the Augurs commander.",
                        buttons);
            }
            if (player.getLeaderIDs().contains("kortalicommander")
                    && !player.hasLeaderUnlocked("kortalicommander")) {
                buttons = new ArrayList<>();
                buttons.add(Buttons.green("unlockCommander_kortali", "Unlock Kortali commander", Emojis.kortali));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                                + " this is a reminder that if you destroy all of the opponent's units in this system, you may use the button to unlock Queen Lorena, the Kortali commander.",
                        buttons);
            }
        }

    }

    private static void sendAFBButtonsToThread(GenericInteractionCreateEvent event, ThreadChannel threadChannel, Game game, List<Player> combatPlayers, Tile tile) {
        boolean thereAreAFBUnits = false;
        for (Player player : combatPlayers) {
            if (!CombatHelper.GetUnitsInAFB(tile, player, event).isEmpty())
                thereAreAFBUnits = true;
        }
        if (!thereAreAFBUnits)
            return;

        List<Button> afbButtons = new ArrayList<>();
        afbButtons.add(Buttons.gray("combatRoll_" + tile.getPosition() + "_space_afb", "Roll " + CombatRollType.AFB.getValue()));
        MessageHelper.sendMessageToChannelWithButtons(threadChannel, "Buttons to roll AFB:", afbButtons);
        for (Player player : combatPlayers) {
            if (ButtonHelper.doesPlayerHaveMechHere("naalu_mech", player, tile) && !ButtonHelper.isLawInPlay(game, "articles_war")) {
                MessageHelper.sendMessageToChannel(threadChannel, "Reminder that you cannot use AFB against " + player.getFactionEmojiOrColor() + " due to their mech power");
            }
        }

    }

    private static List<Button> getSpaceCannonButtons(Game game, Player activePlayer, Tile tile) {
        List<Button> spaceCannonButtons = new ArrayList<>();
        spaceCannonButtons.add(Buttons.gray("combatRoll_" + tile.getPosition() + "_space_spacecannonoffence",
                "Roll Space Cannon Offence"));
        if (game.isFowMode())
            return spaceCannonButtons;
        spaceCannonButtons.add(Buttons.red("declinePDS", "Decline PDS"));

        // Add Graviton Laser System button if applicable
        for (Player playerWithPds : ButtonHelper.tileHasPDS2Cover(activePlayer, game, tile.getPosition())) {
            if (playerWithPds.hasTechReady("gls")) { // Graviton Laser Systems
                spaceCannonButtons.add(Buttons.gray("exhaustTech_gls", "Exhaust Graviton Laser System", Emojis.CyberneticTech));
                break;
            }
        }
        return spaceCannonButtons;
    }

    private static List<Button> getStartOfSpaceCombatButtons(Game game, Player p1, Player p2, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        if (game.isFowMode())
            return buttons;

        // Assault Cannon
        if ((p1.hasTech("asc") && (ButtonHelper.checkNumberNonFighterShips(p1, game, tile) > 2 || ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", p1, tile)))
                || (p2.hasTech("asc") && (ButtonHelper.checkNumberNonFighterShips(p2, game, tile) > 2 || ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", p2, tile)))) {
            buttons.add(Buttons.blue("assCannonNDihmohn_asc_" + tile.getPosition(), "Use Assault Cannon", Emojis.WarfareTech));
        }

        // Dimensional Splicer
        if (FoWHelper.doesTileHaveWHs(game, tile.getPosition()) && (p1.hasTech("ds") || p2.hasTech("ds"))) {
            buttons.add(Buttons.blue("assCannonNDihmohn_ds_" + tile.getPosition(), "Use Dimensional Splicer", Emojis.Ghost));
        }

        if ((p1.hasAbility("shroud_of_lith")
                && ButtonHelperFactionSpecific.getKolleccReleaseButtons(p1, game).size() > 1)
                || (p2.hasAbility("shroud_of_lith")
                && ButtonHelperFactionSpecific.getKolleccReleaseButtons(p2, game).size() > 1)) {
            buttons.add(Buttons.blue("shroudOfLithStart", "Use Shroud of Lith", Emojis.kollecc));
        }

        // Dihmohn Commander
        if ((game.playerHasLeaderUnlockedOrAlliance(p1, "dihmohncommander")
                && ButtonHelper.checkNumberNonFighterShips(p1, game, tile) > 2)
                || (game.playerHasLeaderUnlockedOrAlliance(p2, "dihmohncommander")
                && ButtonHelper.checkNumberNonFighterShips(p2, game, tile) > 2)) {
            buttons.add(Buttons.blue("assCannonNDihmohn_dihmohn_" + tile.getPosition(), "Use Dih-Mohn Commander", Emojis.dihmohn));
        }

        // Ambush
        if ((p1.hasAbility("ambush")) || p2.hasAbility("ambush")) {
            buttons.add(Buttons.gray("rollForAmbush_" + tile.getPosition(), "Ambush", Emojis.Mentak));
        }

        if ((p1.hasLeaderUnlocked("mentakhero")) || p2.hasLeaderUnlocked("mentakhero")) {
            buttons.add(Buttons.gray("purgeMentakHero_" + tile.getPosition(), "Purge Mentak Hero", Emojis.Mentak));
        }

        if ((p1.hasAbility("facsimile") && p1 != game.getActivePlayer())
                || p2.hasAbility("facsimile") && p2 != game.getActivePlayer() && !game.isFowMode()) {
            buttons.add(Buttons.gray("startFacsimile_" + tile.getPosition(), "Facsimile", Emojis.mortheus));
        }

        // mercenaries
        Player florzen = Helper.getPlayerFromAbility(game, "mercenaries");
        if (florzen != null && FoWHelper.playerHasFightersInAdjacentSystems(florzen, tile, game)) {
            buttons.add(Buttons.gray(florzen.getFinsFactionCheckerPrefix() + "mercenariesStep1_" + tile.getPosition(), "Mercenaries", Emojis.florzen));
        }
        return buttons;
    }

    /**
     * # of extra rings to show around the tile image
     *
     * @return 0 if no PDS2 nearby, 1 if PDS2 is nearby
     */
    private static int getTileImageContextForPDS2(Game game, Player player1, Tile tile, String spaceOrGround) {
        if (game.isFowMode() || "ground".equalsIgnoreCase(spaceOrGround)) {
            return 0;
        }
        if (!ButtonHelper.tileHasPDS2Cover(player1, game, tile.getPosition()).isEmpty()) {
            return 1;
        }
        return 0;
    }

    private static void sendGeneralCombatButtonsToThread(ThreadChannel threadChannel, Game game, Player player1,
                                                         Player player2, Tile tile, String spaceOrGround, GenericInteractionCreateEvent event) {
        List<Button> buttons = getGeneralCombatButtons(game, tile.getPosition(), player1, player2, spaceOrGround,
                event);
        MessageHelper.sendMessageToChannelWithButtons(threadChannel, "Buttons for Combat:", buttons);
    }

    // TODO: Break apart into: [all combats, space combat, ground combat] methods
    public static List<Button> getGeneralCombatButtons(Game game, String pos, Player p1, Player p2, String groundOrSpace, GenericInteractionCreateEvent event) {
        Tile tile = game.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();
        UnitHolder space = tile.getUnitHolders().get("space");
        boolean isSpaceCombat = "space".equalsIgnoreCase(groundOrSpace);
        boolean isGroundCombat = "ground".equalsIgnoreCase(groundOrSpace);

        if ("justPicture".equalsIgnoreCase(groundOrSpace)) {
            buttons.add(Buttons.blue(
                    "refreshViewOfSystem_" + pos + "_" + p1.getFaction() + "_" + p2.getFaction() + "_" + groundOrSpace,
                    "Refresh Picture"));
            return buttons;
        }
        buttons.add(Buttons.red("getDamageButtons_" + pos + "_" + groundOrSpace + "combat", "Assign Hits"));
        // if (getButtonsForRepairingUnitsInASystem(p1, game, tile).size() > 1 ||
        // getButtonsForRepairingUnitsInASystem(p2, game, tile).size() > 1) {
        buttons.add(Buttons.green("getRepairButtons_" + pos, "Repair Damage"));
        // }
        buttons.add(Buttons.blue(
                "refreshViewOfSystem_" + pos + "_" + p1.getFaction() + "_" + p2.getFaction() + "_" + groundOrSpace,
                "Refresh Picture"));

        Player titans = Helper.getPlayerFromUnlockedLeader(game, "titansagent");
        if (!game.isFowMode() && titans != null && titans.hasUnexhaustedLeader("titansagent")) {
            String finChecker = "FFCC_" + titans.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "exhaustAgent_titansagent",
                    "Use Ul Agent", Emojis.Titans));
        }
        if (p1.hasTechReady("sc") || (!game.isFowMode() && p2.hasTechReady("sc"))) {
            // TemporaryCombatModifierModel combatModAC =
            // CombatTempModHelper.GetPossibleTempModifier("tech", "sc",
            // p1.getNumberTurns());
            buttons.add(Buttons.green("applytempcombatmod__" + "tech" + "__" + "sc", "Use Supercharge", Emojis.Naaz));
        }

        Player ghemina = Helper.getPlayerFromUnlockedLeader(game, "gheminaagent");
        if (!game.isFowMode() && ghemina != null && ghemina.hasUnexhaustedLeader("gheminaagent")) {
            String finChecker = "FFCC_" + ghemina.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "exhaustAgent_gheminaagent",
                    "Use Ghemina Agents", Emojis.ghemina));
        }
        Player khal = Helper.getPlayerFromUnlockedLeader(game, "kjalengardagent");
        if (!game.isFowMode() && khal != null && khal.hasUnexhaustedLeader("kjalengardagent")) {
            String finChecker = "FFCC_" + khal.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "exhaustAgent_kjalengardagent",
                    "Use Kjalengard Agent", Emojis.kjalengard));
        }
        Player sol = Helper.getPlayerFromUnlockedLeader(game, "solagent");
        if (!game.isFowMode() && sol != null && sol.hasUnexhaustedLeader("solagent") && isGroundCombat) {
            String finChecker = "FFCC_" + sol.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "getAgentSelection_solagent",
                    "Use Sol Agent", Emojis.Sol));
        }
        Player kyro = Helper.getPlayerFromUnlockedLeader(game, "kyroagent");
        if (!game.isFowMode() && kyro != null && kyro.hasUnexhaustedLeader("kyroagent") && isGroundCombat) {
            String finChecker = "FFCC_" + kyro.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "getAgentSelection_kyroagent",
                    "Use Kyro Agent", Emojis.blex));

        }

        Player letnev = Helper.getPlayerFromUnlockedLeader(game, "letnevagent");
        if ((!game.isFowMode() || letnev == p1) && letnev != null && letnev.hasUnexhaustedLeader("letnevagent") && "space".equalsIgnoreCase(groundOrSpace)) {
            buttons.add(Buttons.gray(letnev.finChecker() + "getAgentSelection_letnevagent", "Use Letnev Agent", Emojis.Letnev));
        }
        // Exo 2s
        if ("space".equalsIgnoreCase(groundOrSpace) && !game.isFowMode()) {
            if ((tile.getSpaceUnitHolder().getUnitCount(UnitType.Dreadnought, p1.getColor()) > 0 && p1.hasTech("exo2")) || (tile.getSpaceUnitHolder().getUnitCount(UnitType.Dreadnought, p2.getColor()) > 0 && p2.hasTech("exo2"))) {
                buttons.add(Buttons.blue("assCannonNDihmohn_exo_" + tile.getPosition(), "Use Exotrireme 2 Ability", Emojis.Sardakk));
            }
        }

        Player nomad = Helper.getPlayerFromUnlockedLeader(game, "nomadagentthundarian");
        if ((!game.isFowMode() || nomad == p1) && nomad != null && nomad.hasUnexhaustedLeader("nomadagentthundarian")) {
            String finChecker = "FFCC_" + nomad.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "exhaustAgent_nomadagentthundarian", "Use The Thundarian", Emojis.Nomad));
        }

        Player yin = Helper.getPlayerFromUnlockedLeader(game, "yinagent");
        if ((!game.isFowMode() || yin == p1) && yin != null && yin.hasUnexhaustedLeader("yinagent")) {
            String finChecker = "FFCC_" + yin.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "yinagent_" + pos,
                    "Use Yin Agent", Emojis.Yin));
        }

        if ((p2.hasUnexhaustedLeader("kortaliagent")) && !game.isFowMode() && isGroundCombat
                && !p1.getFragments().isEmpty()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "exhaustAgent_kortaliagent_" + p1.getColor(), "Use Kortali Agent", Emojis.kortali));
        }
        if (p1.hasUnexhaustedLeader("kortaliagent") && isGroundCombat && !p2.getFragments().isEmpty()) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "exhaustAgent_kortaliagent_" + p2.getColor(), "Use Kortali Agent", Emojis.kortali));
        }

        if ((p2.hasAbility("glory")) && !game.isFowMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            if (!ButtonHelperAgents.getGloryTokensLeft(game).isEmpty()) {
                buttons.add(Buttons.gray(finChecker + "placeGlory_" + pos, "Place Glory Token (Upon Win)", Emojis.kjalengard));
            } else {
                buttons.add(Buttons.gray(finChecker + "moveGloryStart_" + pos, "Move Glory Token (Upon Win)", Emojis.kjalengard));
            }
            if (p2.getStrategicCC() > 0) {
                buttons.add(Buttons.gray(finChecker + "gloryTech", "Research Unit Upgrade (Upon Win)", Emojis.kjalengard));
            }
        }
        if (p1.hasAbility("glory") && ButtonHelperAgents.getGloryTokenTiles(game).size() < 3) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            if (!ButtonHelperAgents.getGloryTokensLeft(game).isEmpty()) {
                buttons.add(Buttons.gray(finChecker + "placeGlory_" + pos, "Place Glory Token (Upon Win)", Emojis.kjalengard));
            } else {
                buttons.add(Buttons.gray(finChecker + "moveGloryStart_" + pos, "Move Glory Token (Upon Win)", Emojis.kjalengard));
            }
            if (p1.getStrategicCC() > 0) {
                buttons.add(Buttons.gray(finChecker + "gloryTech", "Research Unit Upgrade (Upon Win)", Emojis.kjalengard));
            }
        }

        if ((p2.hasAbility("collateralized_loans")) && !game.isFowMode()
                && p2.getDebtTokenCount(p1.getColor()) > 0 && groundOrSpace.equalsIgnoreCase("space")) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "collateralizedLoans_" + pos + "_" + p1.getFaction(), "Collateralized Loans", Emojis.vaden));
        }
        if ((p1.hasAbility("collateralized_loans"))
                && p1.getDebtTokenCount(p2.getColor()) > 0 && groundOrSpace.equalsIgnoreCase("space")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "collateralizedLoans_" + pos + "_" + p2.getFaction(), "Collateralized Loans", Emojis.vaden));
        }

        if (p2.hasAbility("necrophage") && !game.isFowMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "offerNecrophage", "Necrophage").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }
        if (p1.hasAbility("necrophage")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "offerNecrophage", "Necrophage").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }

        boolean hasDevotionShips = space != null && (space.getUnitCount(UnitType.Destroyer, p2) > 0 || space.getUnitCount(UnitType.Cruiser, p2) > 0);
        if (p2.hasAbility("devotion") && !game.isFowMode() && isSpaceCombat && hasDevotionShips) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "startDevotion_" + tile.getPosition(), "Devotion", Emojis.Yin));
        }
        hasDevotionShips = space != null && (space.getUnitCount(UnitType.Destroyer, p1) > 0 || space.getUnitCount(UnitType.Cruiser, p1) > 0);
        if (p1.hasAbility("devotion") && isSpaceCombat && hasDevotionShips) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "startDevotion_" + tile.getPosition(), "Devotion", Emojis.Yin));
        }

        // if (isSpaceCombat && game.playerHasLeaderUnlockedOrAlliance(p2,
        // "mentakcommander") && !game.isFoWMode()) {
        // String finChecker = "FFCC_" + p2.getFaction() + "_";
        // buttons.add(Buttons.gray(finChecker + "mentakCommander_" + p1.getColor(),
        // "Mentak Commander on " +
        // p1.getColor(), Emojis.Mentak);
        // }
        // if (isSpaceCombat && ((p1.hasTech("so")) || (!game.isFoWMode() &&
        // p2.hasTech("so")))) {
        // buttons.add(Buttons.gray("salvageOps_" + tile.getPosition(), "Salvage
        // Ops", Emojis.Mentak);
        // }
        // if (isSpaceCombat && game.playerHasLeaderUnlockedOrAlliance(p1,
        // "mentakcommander")) {
        // String finChecker = "FFCC_" + p1.getFaction() + "_";
        // buttons.add(Buttons.gray(finChecker + "mentakCommander_" + p2.getColor(),
        // "Mentak Commander on " +
        // p2.getColor(), Emojis.Mentak);
        // }

        if (isSpaceCombat && game.playerHasLeaderUnlockedOrAlliance(p2, "mykomentoricommander")
                && !game.isFowMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "resolveMykoCommander", "Spend For Myko-Mentori Commander").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }
        if (isSpaceCombat && game.playerHasLeaderUnlockedOrAlliance(p1, "mykomentoricommander")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "resolveMykoCommander", "Spend For Myko-Mentori Commander").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }

        if (isSpaceCombat && p2.hasAbility("munitions") && !game.isFowMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "munitionsReserves", "Use Munitions Reserves", Emojis.Letnev));
        }
        if (isSpaceCombat && p1.hasAbility("munitions")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "munitionsReserves", "Use Munitions Reserves", Emojis.Letnev));
        }

        if (isSpaceCombat && ButtonHelper.doesPlayerHaveFSHere("mykomentori_flagship", p2, tile) && !game.isFowMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "gain_1_comms_stay", "Psyclobea Qarnyx (Myko Flagship)").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }
        if (isSpaceCombat && ButtonHelper.doesPlayerHaveFSHere("mykomentori_flagship", p1, tile)) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "gain_1_comms_stay", "Psyclobea Qarnyx (Myko Flagship)").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }

        if (isSpaceCombat) {
            buttons.add(Buttons.gray("announceARetreat", "Announce A Retreat"));
            buttons.add(Buttons.red("retreat_" + pos, "Retreat"));
        }
        if (isSpaceCombat && p2.hasAbility("foresight") && p2.getStrategicCC() > 0 && !game.isFowMode()) {
            buttons.add(Buttons.red("retreat_" + pos + "_foresight", "Foresight", Emojis.Naalu));
        }
        if (isSpaceCombat && p1.hasAbility("foresight") && p1.getStrategicCC() > 0) {
            buttons.add(Buttons.red("retreat_" + pos + "_foresight", "Foresight", Emojis.Naalu));
        }
        boolean gheminaCommanderApplicable = false;
        if (tile.getPlanetUnitHolders().isEmpty()) {
            gheminaCommanderApplicable = true;
        } else {
            for (Player p3 : game.getRealPlayers()) {
                if (ButtonHelper.getTilesOfPlayersSpecificUnits(game, p3, UnitType.Pds, UnitType.Spacedock)
                        .contains(tile)) {
                    gheminaCommanderApplicable = true;
                    break;
                }
            }
        }
        if (isSpaceCombat && game.playerHasLeaderUnlockedOrAlliance(p2, "gheminacommander")
                && gheminaCommanderApplicable && !game.isFowMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.red(finChecker + "declareUse_Ghemina Commander", "Use Ghemina Commanders", Emojis.ghemina));
        }
        if (isSpaceCombat && game.playerHasLeaderUnlockedOrAlliance(p1, "gheminacommander")
                && gheminaCommanderApplicable) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.red(finChecker + "declareUse_Ghemina Commander", "Use Ghemina Commanders", Emojis.ghemina));
        }
        if (p1.hasLeaderUnlocked("keleresherokuuasi") && isSpaceCombat
                && ButtonHelper.doesPlayerOwnAPlanetInThisSystem(tile, p1, game)) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "purgeKeleresAHero", "Purge Keleres (Argent) Hero", Emojis.Keleres));
        }
        if (p2.hasLeaderUnlocked("keleresherokuuasi") && !game.isFowMode() && isSpaceCombat
                && ButtonHelper.doesPlayerOwnAPlanetInThisSystem(tile, p1, game)) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "purgeKeleresAHero", "Purge Keleres (Argent) Hero", Emojis.Keleres));
        }

        if (p1.hasLeaderUnlocked("dihmohnhero") && isSpaceCombat) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "purgeDihmohnHero", "Purge Dih-Mohn Hero", Emojis.dihmohn));
        }
        if (p2.hasLeaderUnlocked("dihmohnhero") && !game.isFowMode() && isSpaceCombat) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "purgeDihmohnHero", "Purge Dih-Mohn Hero", Emojis.dihmohn));
        }

        if (p1.hasLeaderUnlocked("kortalihero")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "purgeKortaliHero_" + p2.getFaction(), "Purge Kortali Hero", Emojis.dihmohn));
        }
        if (p2.hasLeaderUnlocked("kortalihero") && !game.isFowMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "purgeKortaliHero_" + p1.getFaction(), "Purge Kortali Hero", Emojis.kortali));
        }

        if (ButtonHelper.getTilesOfUnitsWithBombard(p1, game).contains(tile)
                || ButtonHelper.getTilesOfUnitsWithBombard(p2, game).contains(tile)) {
            if (tile.getUnitHolders().size() > 2) {
                buttons.add(Buttons.gray(
                        "bombardConfirm_combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment,
                        "Roll Bombardment"));
            } else {
                buttons.add(
                        Buttons.gray("combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment,
                                "Roll Bombardment"));
            }
        }
        if (game.playerHasLeaderUnlockedOrAlliance(p1, "cheirancommander") && isGroundCombat
                && p1 != game.getActivePlayer()) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "cheiranCommanderBlock_hm", "Block with Cheiran Commander", Emojis.cheiran));
        }
        if (!game.isFowMode() && game.playerHasLeaderUnlockedOrAlliance(p2, "cheirancommander")
                && isGroundCombat
                && p2 != game.getActivePlayer()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "cheiranCommanderBlock_hm", "Block with Cheiran Commander", Emojis.cheiran));
        }
        if (p1.hasTechReady("absol_x89") && isGroundCombat && p1 != game.getActivePlayer()) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.green(finChecker + "exhaustTech_absol_x89", "X-89 Bacterial Weapon", Emojis.BioticTech));
        }
        if (!game.isFowMode() && p2.hasTechReady("absol_x89") && isGroundCombat
                && p2 != game.getActivePlayer()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.green(finChecker + "exhaustTech_absol_x89", "X-89 Bacterial Weapon", Emojis.BioticTech));
        }
        if (game.playerHasLeaderUnlockedOrAlliance(p1, "kortalicommander")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "kortaliCommanderBlock_hm", "Block with Kortali Commander", Emojis.kortali));
        }
        if (!game.isFowMode() && game.playerHasLeaderUnlockedOrAlliance(p2, "kortalicommander")) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(finChecker + "kortaliCommanderBlock_hm", "Block with Kortali Commander", Emojis.kortali));
        }
        for (UnitHolder unitH : tile.getUnitHolders().values()) {
            String nameOfHolder = "Space";
            if (unitH instanceof Planet) {
                nameOfHolder = Helper.getPlanetRepresentation(unitH.getName(), game);
                for (Player p : List.of(p1, p2)) {
                    // Sol Commander
                    if (p != game.getActivePlayer() && game.playerHasLeaderUnlockedOrAlliance(p, "solcommander") && isGroundCombat) {
                        String id = p.finChecker() + "utilizeSolCommander_" + unitH.getName();
                        String label = "Use Sol Commander on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, Emojis.Sol));
                    }
                    // Yin Indoctrinate
                    if (p.hasAbility("indoctrination") && isGroundCombat) {
                        String id = p.finChecker() + "initialIndoctrination_" + unitH.getName();
                        String label = "Indoctrinate on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, Emojis.Yin));
                    }
                    // Letnev Mech
                    if (p.hasUnit("letnev_mech") && isGroundCombat && unitH.getUnitCount(UnitType.Infantry, p1.getColor()) > 0
                            && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p1, "mech") < 4) {
                        String id = p.finChecker() + "letnevMechRes_" + unitH.getName() + "_mech";
                        String label = "Deploy Dunlain Reaper on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, Emojis.Letnev));
                    }
                    // Assimilate
                    if (p1.hasAbility("assimilate") && isGroundCombat
                            && (unitH.getUnitCount(UnitType.Spacedock, p2.getColor()) > 0
                            || unitH.getUnitCount(UnitType.CabalSpacedock, p2.getColor()) > 0
                            || unitH.getUnitCount(UnitType.Pds, p2.getColor()) > 0)) {
                        String id = p.finChecker() + "assimilate_" + unitH.getName();
                        String label = "Assimilate Structures on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, Emojis.L1Z1X));
                    }
                }
                // vaden mechs are asymmetricish
                if (p1.hasUnit("vaden_mech") && unitH.getUnitCount(UnitType.Mech, p1) > 0 && isGroundCombat && p1.getDebtTokenCount(p2.getColor()) > 0) {
                    String id = p1.finChecker() + "resolveVadenMech_" + unitH.getName() + "_" + p2.getColor();
                    String label = "Vaden Mech Ability on " + nameOfHolder;
                    buttons.add(Buttons.gray(id, label, Emojis.vaden));
                }
                if (p2.hasUnit("vaden_mech") && unitH.getUnitCount(UnitType.Mech, p2) > 0 && isGroundCombat && p2.getDebtTokenCount(p1.getColor()) > 0) {
                    String id = p2.finChecker() + "resolveVadenMech_" + unitH.getName() + "_" + p1.getColor();
                    String label = "Vaden Mech Ability on " + nameOfHolder;
                    buttons.add(Buttons.gray(id, label, Emojis.vaden));
                }
            }
            if ("space".equalsIgnoreCase(nameOfHolder) && isSpaceCombat) {
                buttons.add(Buttons.gray("combatRoll_" + pos + "_" + unitH.getName(), "Roll Space Combat"));
                if (p1.isDummy()) {
                    buttons.add(Buttons.gray(p1.dummyPlayerSpoof() + "combatRoll_" + pos + "_" + unitH.getName(), "Roll Space Combat For Dummy").withEmoji(Emoji.fromFormatted(p1.getFactionEmoji())));
                }
                if (p2.isDummy()) {
                    buttons.add(Buttons.gray(p2.dummyPlayerSpoof() + "combatRoll_" + pos + "_" + unitH.getName(), "Roll Space Combat For Dummy").withEmoji(Emoji.fromFormatted(p2.getFactionEmoji())));
                }
            } else {
                if (!isSpaceCombat && !"space".equalsIgnoreCase(nameOfHolder)) {
                    buttons.add(Buttons.gray("combatRoll_" + pos + "_" + unitH.getName(),
                            "Roll Ground Combat for " + nameOfHolder));
                    Player nonActive = p1;
                    if (p1 == game.getActivePlayer()) {
                        nonActive = p2;
                    }
                    if (p1.isDummy()) {
                        buttons.add(Buttons.gray(p1.dummyPlayerSpoof() + "combatRoll_" + pos + "_" + unitH.getName(), "Roll Ground Combat for " + nameOfHolder + " for Dummy").withEmoji(Emoji.fromFormatted(p1.getFactionEmoji())));
                    }
                    if (p2.isDummy()) {
                        buttons.add(Buttons.gray(p2.dummyPlayerSpoof() + "combatRoll_" + pos + "_" + unitH.getName(), "Roll Ground Combat for " + nameOfHolder + " for Dummy").withEmoji(Emoji.fromFormatted(p2.getFactionEmoji())));
                    }
                    if (checkIfUnitsOfType(nonActive, game, event, tile, unitH.getName(), CombatRollType.SpaceCannonDefence)) {
                        buttons.add(Buttons.gray(
                                "combatRoll_" + tile.getPosition() + "_" + unitH.getName() + "_spacecannondefence",
                                "Roll Space Cannon Defence for " + nameOfHolder));
                    }
                }
            }
        }
        return buttons;
    }

    private static String getSpaceCombatIntroMessage() {
        return """
                ## Steps for Space Combat:
                > 1. End of movement abilities (Foresight, Stymie, etc.)
                > 2. Firing of PDS
                > 3. Start of Combat (Skilled Retreat, Morale Boost, etc.)
                > 4. Anti-Fighter Barrage
                > 5. Declare Retreats (including Rout)
                > 6. Roll Dice!
                """;
    }

    private static String getGroundCombatIntroMessage() {
        return """
                ## Steps for Invasion:
                > 1. Start of invasion abilities (Tekklar, Blitz, Bunker, etc.)
                > 2. Bombardment
                > 3. Commit Ground Forces
                > 4. After commit window (Parley, Ghost Squad, etc.)
                > 5. Start of Combat (Morale Boost, etc.)
                > 6. Roll Dice!
                """;
    }

    public static boolean checkIfUnitsOfType(Player player, Game game, GenericInteractionCreateEvent event, Tile tile, String unitHolderName, CombatRollType rollType) {
        UnitHolder combatOnHolder = tile.getUnitHolders().get(unitHolderName);
        Map<UnitModel, Integer> playerUnitsByQuantity = CombatHelper.GetUnitsInCombat(tile, combatOnHolder, player, event,
                rollType, game);
        return !playerUnitsByQuantity.isEmpty();
    }
}
