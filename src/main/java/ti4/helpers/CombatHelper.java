package ti4.helpers;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;

import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.UnitModel;

public class CombatHelper {

    

    public static HashMap<UnitModel, Integer> GetAllUnits(UnitHolder unitHolder, Player player, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        HashMap<String, Integer> unitsByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        Map<UnitModel, Integer> unitsInCombat = unitsByAsyncId.entrySet().stream().flatMap(
            entry -> player.getUnitsByAsyncID(entry.getKey()).stream().map(x -> new ImmutablePair<>(x, entry.getValue()))
        ).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        HashMap<UnitModel, Integer> output;
       
        output = new HashMap<>(unitsInCombat.entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
        
        Set<String> duplicates = new HashSet<>();
        List<String> dupes = output.keySet().stream()
            .filter(unit -> !duplicates.add(unit.getAsyncId()))
            .map(UnitModel::getBaseType)
            .toList();
        for(int x = 1; x < dupes.size(); x++){
            String dupe = dupes.get(x);
            for(UnitModel mod : output.keySet()){
                if(mod.getBaseType().equalsIgnoreCase(dupe)){
                    output.put(mod, 0);
                    break;
                }
            }

        }

        
        


        return output;
    }

    public static HashMap<UnitModel, Integer> GetUnitsInCombat(UnitHolder unitHolder, Player player, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        HashMap<String, Integer> unitsByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        Map<UnitModel, Integer> unitsInCombat = unitsByAsyncId.entrySet().stream().map(
            entry -> new ImmutablePair<>
                (
                    player.getPriorityUnitByAsyncID(entry.getKey()),
                    entry.getValue()
                )
        ).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        HashMap<UnitModel, Integer> output;
        if (unitHolder.getName().equals(Constants.SPACE)) {
            output = new HashMap<>(unitsInCombat.entrySet().stream()
                .filter(entry -> entry.getKey().getIsShip() != null && entry.getKey().getIsShip())
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
        } else {
            output = new HashMap<>(unitsInCombat.entrySet().stream()
                .filter(entry -> entry.getKey().getIsGroundForce() != null && entry.getKey().getIsGroundForce())
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

    public static Player GetOpponent(Player player, UnitHolder unitHolder, Game activeGame) {
        Player opponent = null;
        String playerColorID = Mapper.getColorID(player.getColor());
        Optional<String> opponentColor = unitHolder.getUnitColorsOnHolder().stream()
                .filter(color -> !color.equals(playerColorID))
                .findFirst();
        if (opponentColor.isPresent()) {
            Optional<Player> potentialPlayer = activeGame.getRealPlayers().stream()
                    .filter(otherPlayer -> Mapper.getColorID(otherPlayer.getColor()).equals(opponentColor.get()))
                    .findFirst();
            if(potentialPlayer.isPresent()){
                opponent  = potentialPlayer.get();
            }
        }
        return opponent;
    }

    public static String RollForUnits(Map<UnitModel, Integer> units,
            HashMap<String, Integer> extraRolls, List<NamedCombatModifierModel> customMods, List<NamedCombatModifierModel> autoMods, Player player, Player opponent,
            Game activeGame) {
        String result = "";

        List<NamedCombatModifierModel> mods = new ArrayList<>(customMods);
        mods.addAll(autoMods);
        result += CombatModHelper.GetModifiersText("With automatic modifiers: \n", units, autoMods);
        result += CombatModHelper.GetModifiersText("With custom modifiers: \n", units, customMods);

        // Display extra rolls info
        List<UnitModel> unitsWithExtraRolls = units.keySet().stream()
                .filter(unit -> extraRolls.containsKey(unit.getAsyncId()))
                .toList();
        if (!extraRolls.isEmpty()) {
            result += "With ";
            List<String> extraRollMessages = new ArrayList<>();
            for (UnitModel unit : unitsWithExtraRolls) {
                String plusPrefix = "+";
                Integer numExtraRolls = extraRolls.get(unit.getAsyncId());
                String unitAsnycEmoji = Helper.getEmojiFromDiscord(unit.getBaseType());
                if (numExtraRolls < 0) {
                    plusPrefix = "";
                }
                extraRollMessages.add(String.format("%s%s rolls for %s", plusPrefix, numExtraRolls, unitAsnycEmoji));
            }
            result += String.join(", ", extraRollMessages) + "\n";
        }

        // Actually roll for each unit
        int totalHits = 0;
        StringBuilder resultBuilder = new StringBuilder(result);
        for (Map.Entry<UnitModel, Integer> entry : units.entrySet()) {
            UnitModel unit = entry.getKey();
            int numOfUnit = entry.getValue();

            int toHit = unit.getCombatHitsOn();
            int modifierToHit = CombatModHelper.GetCombinedModifierForUnit(unit, mods, player, opponent, activeGame);
            int extraRollsForUnit = 0;
            if (extraRolls.containsKey(unit.getAsyncId())) {
                extraRollsForUnit = extraRolls.get(unit.getAsyncId());
            }
            int numRollsPerUnit = unit.getCombatDieCount();
            int numRolls = (numOfUnit * numRollsPerUnit) + extraRollsForUnit;
            int[] resultRolls = new int[numRolls];
            for (int index = 0; index < numRolls; index++) {
                Random random = new Random();
                int min = 1;
                int max = 10;
                resultRolls[index] = random.nextInt(max - min + 1) + min;
            }

            int[] hitRolls = Arrays.stream(resultRolls)
                    .filter(roll -> roll >= toHit - modifierToHit)
                    .toArray();

            String rollsSuffix = "";
            totalHits += hitRolls.length;
            if (hitRolls.length > 1) {
                rollsSuffix = "s";
            }

            String unitTypeHitsInfo = String.format("hits on %s", toHit);
            if (unit.getCombatDieCount() > 1) {
                unitTypeHitsInfo = String.format("%s rolls, hits on %s", unit.getCombatDieCount(), toHit);
            }
            if (modifierToHit != 0) {
                String modifierToHitString = Integer.toString(modifierToHit);
                if (modifierToHit > 0) {
                    modifierToHitString = "+" + modifierToHitString;
                }

                if ((toHit - modifierToHit) <= 1) {
                    unitTypeHitsInfo = String.format("always hits (%s mods)",
                            modifierToHitString);
                    if (unit.getCombatDieCount() > 1) {
                        unitTypeHitsInfo = String.format("%s rolls, always hits (%s mods)", unit.getCombatDieCount(),
                                modifierToHitString);
                    }
                } else {
                    unitTypeHitsInfo = String.format("hits on %s (%s mods)", (toHit - modifierToHit),
                            modifierToHitString);
                    if (unit.getCombatDieCount() > 1) {
                        unitTypeHitsInfo = String.format("%s rolls, hits on %s (%s mods)", unit.getCombatDieCount(),
                                (toHit - modifierToHit), modifierToHitString);
                    }
                }

            }
            String upgradedUnitName = "";
            if (!StringUtils.isBlank(unit.getUpgradesFromUnitId()) || !StringUtils.isBlank(unit.getFaction())) {
                upgradedUnitName = String.format(" %s", unit.getName());
            }
            String unitEmoji = Helper.getEmojiFromDiscord(unit.getBaseType());
            resultBuilder.append(String.format("%s %s%s %s %s - %s hit%s\n", numOfUnit, unitEmoji, upgradedUnitName,
                unitTypeHitsInfo,
                Arrays.toString(resultRolls), hitRolls.length, rollsSuffix));
        }
        result = resultBuilder.toString();

        StringBuilder hitEmojis = new StringBuilder();
        for (int i = 0; i < totalHits; i++) {
            hitEmojis.append(":boom:");
        }
        result += String.format("\n**Total hits %s** %s\n", totalHits, hitEmojis);
        return result;
    }
}