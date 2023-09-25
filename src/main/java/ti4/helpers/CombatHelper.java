package ti4.helpers;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;

import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.UnitModel;

public class CombatHelper {

    public static HashMap<UnitModel, Integer> GetAllUnits(UnitHolder unitHolder, Player player, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        HashMap<String, Integer> unitsByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        Map<UnitModel, Integer> unitsInCombat = unitsByAsyncId.entrySet().stream().flatMap(
            entry -> player.getUnitsByAsyncID(entry.getKey()).stream().map(x -> new ImmutablePair<>(x, entry.getValue()))).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        HashMap<UnitModel, Integer> output;

        output = new HashMap<>(unitsInCombat.entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));

        Set<String> duplicates = new HashSet<>();
        List<String> dupes = output.keySet().stream()
            .filter(unit -> !duplicates.add(unit.getAsyncId()))
            .map(UnitModel::getBaseType)
            .toList();
        for (int x = 1; x < dupes.size(); x++) {
            String dupe = dupes.get(x);
            for (UnitModel mod : output.keySet()) {
                if (mod.getBaseType().equalsIgnoreCase(dupe)) {
                    output.put(mod, 0);
                    break;
                }
            }

        }
        return output;
    }

    public static HashMap<UnitModel, Integer> GetUnitsInCombat(Tile tile, UnitHolder unitHolder, Player player,
        GenericInteractionCreateEvent event, CombatRollType roleType, Game activeGame) {
        //return GetUnitsInCombatRound(unitHolder,player, event);
        switch (roleType) {
            case combatround:
                return GetUnitsInCombatRound(unitHolder, player, event);
            case AFB:
                return GetUnitsInAFB(tile, player, event);
            case bombardment:
                return GetUnitsInBombardment(tile, player, event);
            case SpaceCannonOffence:
                return getUnitsInSpaceCannonOffense(tile, player, event, activeGame);
            default:
                return GetUnitsInCombatRound(unitHolder, player, event);
        }
    }

    public static HashMap<UnitModel, Integer> GetUnitsInCombatRound(UnitHolder unitHolder, Player player, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        HashMap<String, Integer> unitsByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        Map<UnitModel, Integer> unitsInCombat = unitsByAsyncId.entrySet().stream().map(
            entry -> new ImmutablePair<>(
                player.getPriorityUnitByAsyncID(entry.getKey(), unitHolder),
                entry.getValue()))
            .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        HashMap<UnitModel, Integer> output;
        if (unitHolder.getName().equals(Constants.SPACE)) {
            output = new HashMap<>(unitsInCombat.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().getIsShip() != null && entry.getKey().getIsShip())
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
        } else {
            output = new HashMap<>(unitsInCombat.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().getIsGroundForce() != null && entry.getKey().getIsGroundForce())
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
        }
        Set<String> duplicates = new HashSet<>();
        List<String> dupes = output.keySet().stream()
            .filter(unit -> !duplicates.add(unit.getAsyncId()))
            .map(UnitModel::getBaseType)
            .collect(Collectors.toList());
        List<String> missing = unitHolder.getUnitAsyncIdsOnHolder(colorID).keySet().stream()
            .filter(unit -> player.getUnitsByAsyncID(unit.toLowerCase()).isEmpty())
            .collect(Collectors.toList());

        // Gracefully fail when units don't exist
        StringBuilder error = new StringBuilder();
        if (missing.size() > 0) {
            error.append("You do not seem to own any of the following unit types, so they will be skipped.");
            error.append(" Ping bothelper if this seems to be in error.\n");
            error.append("> Unowned units: ").append(missing).append("\n");
        }
        if (dupes.size() > 0) {
            error.append("You seem to own multiple of the following unit types. I will roll all of them, just ignore any that you shouldn't have.\n");
            error.append("> Duplicate units: ").append(dupes);
        }
        if (missing.size() > 0 || dupes.size() > 0) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), error.toString());
        }

        return output;
    }

    public static HashMap<UnitModel, Integer> GetUnitsInAFB(Tile tile, Player player,
        GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());

        HashMap<String, Integer> unitsByAsyncId = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            HashMap<String, Integer> unitsOnHolderByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
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
        Set<String> duplicates = new HashSet<>();
        List<String> dupes = output.keySet().stream()
            .filter(unit -> !duplicates.add(unit.getAsyncId()))
            .map(UnitModel::getBaseType)
            .collect(Collectors.toList());
        List<String> missing = unitsByAsyncId.keySet().stream()
            .filter(unit -> player.getUnitsByAsyncID(unit.toLowerCase()).isEmpty())
            .collect(Collectors.toList());

        // Gracefully fail when units don't exist
        StringBuilder error = new StringBuilder();
        if (missing.size() > 0) {
            error.append("You do not seem to own any of the following unit types, so they will be skipped.");
            error.append(" Ping bothelper if this seems to be in error.\n");
            error.append("> Unowned units: ").append(missing).append("\n");
        }
        if (dupes.size() > 0) {
            error.append("You seem to own multiple of the following unit types. I will roll all of them, just ignore any that you shouldn't have.\n");
            error.append("> Duplicate units: ").append(dupes);
        }
        if (missing.size() > 0 || dupes.size() > 0) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), error.toString());
        }

        return output;
    }

    public static HashMap<UnitModel, Integer> GetUnitsInBombardment(Tile tile, Player player,
        GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        HashMap<String, Integer> unitsByAsyncId = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            HashMap<String, Integer> unitsOnHolderByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
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
        Set<String> duplicates = new HashSet<>();
        List<String> dupes = output.keySet().stream()
            .filter(unit -> !duplicates.add(unit.getAsyncId()))
            .map(UnitModel::getBaseType)
            .collect(Collectors.toList());
        List<String> missing = unitsByAsyncId.keySet().stream()
            .filter(unit -> player.getUnitsByAsyncID(unit.toLowerCase()).isEmpty())
            .collect(Collectors.toList());

        // Gracefully fail when units don't exist
        StringBuilder error = new StringBuilder();
        if (missing.size() > 0) {
            error.append("You do not seem to own any of the following unit types, so they will be skipped.");
            error.append(" Ping bothelper if this seems to be in error.\n");
            error.append("> Unowned units: ").append(missing).append("\n");
        }
        if (dupes.size() > 0) {
            error.append(
                "You seem to own multiple of the following unit types. I will roll all of them, just ignore any that you shouldn't have.\n");
            error.append("> Duplicate units: ").append(dupes);
        }
        if (missing.size() > 0 || dupes.size() > 0) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), error.toString());
        }

        return output;
    }

    public static HashMap<UnitModel, Integer> getUnitsInSpaceCannonOffense(Tile tile, Player player,
        GenericInteractionCreateEvent event, Game activeGame) {
        String colorID = Mapper.getColorID(player.getColor());

        HashMap<String, Integer> unitsByAsyncId = new HashMap<>();

        Collection<UnitHolder> unitHolders = tile.getUnitHolders().values();
        for (UnitHolder unitHolder : unitHolders) {
            HashMap<String, Integer> unitsOnHolderByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
            for (Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {
                Integer existingCount = 0;
                if (unitsByAsyncId.containsKey(unitEntry.getKey())) {
                    existingCount = unitsByAsyncId.get(unitEntry.getKey());
                }
                unitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
            }
        }

        HashMap<String, Integer> adjacentUnitsByAsyncId = new HashMap<>();
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false);
        for (String adjacentTilePosition : adjTiles) {
            if (adjacentTilePosition.equals(tile.getPosition())) {
                continue;
            }
            Tile adjTile = activeGame.getTileByPosition(adjacentTilePosition);
            for (UnitHolder unitHolder : adjTile.getUnitHolders().values()) {
                HashMap<String, Integer> unitsOnHolderByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
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

        // TODO: This could be done better.
        for (UnitHolder unitHolder : unitHolders) {
            if (unitHolder instanceof Planet) {
                Planet planet = (Planet) unitHolder;
                String ccID = Mapper.getControlID(player.getColor());
                if (planet.getControlList().contains(ccID)
                        && planet.getTokenList().contains("attachment_titanshero.png")) {
                    var titanHeroFakeUnit = new UnitModel();
                    titanHeroFakeUnit.setSpaceCannonHitsOn(5);
                    titanHeroFakeUnit.setSpaceCannonDieCount(3);
                    titanHeroFakeUnit.setName(":Titans: Geoform attachment");
                    titanHeroFakeUnit.setAsyncId("titanshero");
                    titanHeroFakeUnit.setId("titanshero");
                    titanHeroFakeUnit.setBaseType("pds");
                    titanHeroFakeUnit.setFaction("Titans");
                    unitsOnTile.put(titanHeroFakeUnit, 1);
                }
            }
        }

        HashMap<UnitModel, Integer> output = new HashMap<>(unitsOnTile.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().getSpaceCannonDieCount() > 0)
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));

        HashMap<UnitModel, Integer> adjacentOutput = new HashMap<>(unitsOnAdjacentTiles.entrySet().stream()
            .filter(entry -> entry.getKey() != null
                && entry.getKey().getSpaceCannonDieCount() > 0
                && entry.getKey().getDeepSpaceCannon() != null && entry.getKey().getDeepSpaceCannon())
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));

        for (var entry : adjacentOutput.entrySet()) {
            if (output.containsKey(entry.getKey())) {
                output.put(entry.getKey(), entry.getValue() + output.get(entry.getKey()));
            } else {
                output.put(entry.getKey(), entry.getValue());
            }
        }

        Set<String> duplicates = new HashSet<>();
        List<String> dupes = output.keySet().stream()
            .filter(unit -> !duplicates.add(unit.getAsyncId()))
            .map(UnitModel::getBaseType)
            .collect(Collectors.toList());
        List<String> missing = unitsByAsyncId.keySet().stream()
            .filter(unit -> player.getUnitsByAsyncID(unit.toLowerCase()).isEmpty())
            .collect(Collectors.toList());

        // Gracefully fail when units don't exist
        StringBuilder error = new StringBuilder();
        if (missing.size() > 0) {
            error.append("You do not seem to own any of the following unit types, so they will be skipped.");
            error.append(" Ping bothelper if this seems to be in error.\n");
            error.append("> Unowned units: ").append(missing).append("\n");
        }
        if (dupes.size() > 0) {
            error.append("You seem to own multiple of the following unit types. I will roll all of them, just ignore any that you shouldn't have.\n");
            error.append("> Duplicate units: ").append(dupes);
        }
        if (missing.size() > 0 || dupes.size() > 0) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), error.toString());
        }

        return output;
    }

    public static Player GetOpponent(Player player, UnitHolder unitHolder, Game activeGame) {
        Player opponent = null;
        String playerColorID = Mapper.getColorID(player.getColor());
        List<Player> opponents = unitHolder.getUnitColorsOnHolder().stream()
            .filter(color -> !color.equals(playerColorID))
            .map(color -> activeGame.getPlayerByColorID(color).get())
            .collect(Collectors.toList());

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

    public static String RollForUnits(Map<UnitModel, Integer> units,
        List<NamedCombatModifierModel> extraRolls, List<NamedCombatModifierModel> customMods,
        List<NamedCombatModifierModel> autoMods, Player player, Player opponent,
        Game activeGame, CombatRollType rollType) {
        String result = "";

        List<NamedCombatModifierModel> mods = new ArrayList<>(customMods);
        mods.addAll(autoMods);
        result += CombatModHelper.GetModifiersText("With automatic modifiers: \n", units, autoMods);
        result += CombatModHelper.GetModifiersText("With custom modifiers: \n", units, customMods);

        // Display extra rolls info
        result += CombatModHelper.GetModifiersText("With automatic extra rolls: \n", units, extraRolls);

        // Actually roll for each unit
        int totalHits = 0;
        StringBuilder resultBuilder = new StringBuilder(result);
        List<UnitModel> unitsList = new ArrayList<>(units.keySet());
        for (Map.Entry<UnitModel, Integer> entry : units.entrySet()) {
            UnitModel unit = entry.getKey();
            int numOfUnit = entry.getValue();

            int toHit = unit.getCombatDieHitsOnForAbility(rollType);
            int modifierToHit = CombatModHelper.GetCombinedModifierForUnit(unit, numOfUnit, mods, player, opponent,
                activeGame,
                unitsList, rollType);
            int extraRollsForUnit = CombatModHelper.GetCombinedModifierForUnit(unit, numOfUnit, extraRolls, player,
                opponent,
                activeGame, unitsList, rollType);
            int numRollsPerUnit = unit.getCombatDieCountForAbility(rollType);

            int numRolls = (numOfUnit * numRollsPerUnit) + extraRollsForUnit;
            int[] resultRolls = new int[numRolls];
            for (int index = 0; index < numRolls; index++) {
                int min = 1;
                int max = 10;
                resultRolls[index] = ThreadLocalRandom.current().nextInt(max - min + 1) + min;
            }

            int[] hitRolls = Arrays.stream(resultRolls)
                .filter(roll -> roll >= toHit - modifierToHit)
                .toArray();

            String hitsSuffix = "";
            totalHits += hitRolls.length;
            if (hitRolls.length > 1) {
                hitsSuffix = "s";
            }

            // Rolls str fragment
            String unitRollsTextInfo = "";
            int totalRolls = unit.getCombatDieCountForAbility(rollType) + extraRollsForUnit;
            if (totalRolls > 1) {
                unitRollsTextInfo = String.format("%s rolls,", unit.getCombatDieCountForAbility(rollType), toHit);
                if (extraRollsForUnit > 0 && unit.getCombatDieCountForAbility(rollType) > 1) {
                    unitRollsTextInfo = String.format("%s rolls (+%s rolls),",
                        unit.getCombatDieCountForAbility(rollType),
                        extraRollsForUnit);
                } else if (extraRollsForUnit > 0) {
                    unitRollsTextInfo = String.format("(+%s rolls),",
                        extraRollsForUnit);
                }
            }

            String unitTypeHitsInfo = String.format("hits on %s", toHit);
            if (modifierToHit != 0) {
                String modifierToHitString = Integer.toString(modifierToHit);
                if (modifierToHit > 0) {
                    modifierToHitString = "+" + modifierToHitString;
                }

                if ((toHit - modifierToHit) <= 1) {
                    unitTypeHitsInfo = String.format("always hits (%s mods)",
                        modifierToHitString);
                } else {
                    unitTypeHitsInfo = String.format("hits on %s (%s mods)", (toHit - modifierToHit),
                        modifierToHitString);
                }
            }
            String upgradedUnitName = "";
            if (!StringUtils.isBlank(unit.getUpgradesFromUnitId()) || !StringUtils.isBlank(unit.getFaction())) {
                upgradedUnitName = String.format(" %s", unit.getName());
            }

            List<String> optionalInfoParts = Arrays.asList(upgradedUnitName, unitRollsTextInfo,
                unitTypeHitsInfo);
            String optionalText = optionalInfoParts.stream().filter(str -> StringUtils.isNotBlank(str))
                .collect(Collectors.joining(" "));

            String unitEmoji = Helper.getEmojiFromDiscord(unit.getBaseType());
            resultBuilder.append(String.format("%s %s%s %s - %s hit%s\n", numOfUnit, unitEmoji, optionalText,
                Arrays.toString(resultRolls), hitRolls.length, hitsSuffix));
        }
        result = resultBuilder.toString();

        StringBuilder hitEmojis = new StringBuilder();
        hitEmojis.append(":boom:".repeat(Math.max(0, totalHits)));
        result += String.format("\n**Total hits %s** %s\n", totalHits, hitEmojis);
        return result;
    }
}