package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import ti4.generator.Mapper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.model.NamedCombatModifierModel;
import ti4.model.UnitModel;

public class CombatHelper {

    public static HashMap<UnitModel, Integer> GetUnitsInCombat(UnitHolder unitHolder, Player player) {
        String colorID = Mapper.getColorID(player.getColor());
        HashMap<UnitModel, Integer> unitsInCombat = new HashMap<>(unitHolder.getUnitAsyncIdsOnHolder(colorID)
                .entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> player.getUnitByAsyncID(entry.getKey().toLowerCase()),
                        Entry::getValue)));

        if (unitHolder.getName() == Constants.SPACE) {
            return new HashMap<UnitModel, Integer>(unitsInCombat.entrySet().stream()
                    .filter(entry -> entry.getKey().getIsShip() != null && entry.getKey().getIsShip())
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
        } else {
            return new HashMap<UnitModel, Integer>(unitsInCombat.entrySet().stream()
                    .filter(entry -> entry.getKey().getIsGroundForce() != null && entry.getKey().getIsGroundForce())
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
        }
    }

    public static Player GetOpponent(Player player, UnitHolder unitHolder, Map activeMap) {
        Player opponent = null;
        String playerColorID = Mapper.getColorID(player.getColor());
        Optional<String> opponentColor = unitHolder.getUnitColorsOnHolder().stream()
                .filter(color -> !color.equals(playerColorID))
                .findFirst();
        if (opponentColor.isPresent()) {
            Optional<Player> potentialPlayer = activeMap.getRealPlayers().stream()
                    .filter(otherPlayer -> Mapper.getColorID(otherPlayer.getColor()).equals(opponentColor.get()))
                    .findFirst();
            if(potentialPlayer.isPresent()){
                opponent  = potentialPlayer.get();
            }
        }
        return opponent;
    }

    public static String RollForUnits(java.util.Map<UnitModel, Integer> units,
            HashMap<String, Integer> extraRolls, List<NamedCombatModifierModel> customMods, List<NamedCombatModifierModel> autoMods, Player player, Player opponent,
            Map map) {
        String result = "";

        List<NamedCombatModifierModel> mods = new ArrayList<>(customMods);
        mods.addAll(autoMods);
        result += CombatModHelper.GetModifiersText("With automatic modifiers: \n", units, autoMods);
        result += CombatModHelper.GetModifiersText("With custom modifiers: \n", units, customMods);

        // Display extra rolls info
        List<UnitModel> unitsWithExtraRolls = units.keySet().stream()
                .filter(unit -> extraRolls.containsKey(unit.getAsyncId()))
                .collect(Collectors.toList());
        if (!extraRolls.isEmpty()) {
            result += "With ";
            ArrayList<String> extraRollMessages = new ArrayList<String>();
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
        for (java.util.Map.Entry<UnitModel, Integer> entry : units.entrySet()) {
            UnitModel unit = entry.getKey();
            int numOfUnit = entry.getValue();

            int toHit = unit.getCombatHitsOn();
            int modifierToHit = CombatModHelper.GetCombinedModifierForUnit(unit, mods, player, opponent, map);
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
                    .filter(roll -> {
                        return roll >= toHit - modifierToHit;
                    })
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
            if (!StringUtils.isBlank(unit.getRequiredTechId())) {
                upgradedUnitName = String.format(" %s", unit.getName());
            }
            String unitEmoji = Helper.getEmojiFromDiscord(unit.getBaseType());
            result += String.format("%s %s%s %s %s - %s hit%s\n", numOfUnit, unitEmoji, upgradedUnitName,
                    unitTypeHitsInfo,
                    Arrays.toString(resultRolls), hitRolls.length, rollsSuffix);
        }

        result += String.format("\n**Total hits %s**\n", totalHits);
        return result.toString();
    }
}