package ti4.commands.special;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.CombatHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;

public class CombatRoll extends SpecialSubcommandData {

    public CombatRoll() {
        super(Constants.COMBAT_ROLL,
                "*BETA* Combat rolls for units on tile. *Auto includes always on mods* (from units, abilities, etc");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.COMBAT_MODIFIERS,
                "+/- <unit type>. Eg -1 all, +2 mech. Temp ACs/PN/exhaust-tech mods should be added here")
                .setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET,
                "(optional) Planet to have combat on. By default rolls for space combat.").setAutoComplete(true)
                .setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.COMBAT_EXTRA_ROLLS,
                "comma list of <count> <unit> eg 2 fighter 1 dreadnought for extra roll")
                .setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "roll for player (by default your units)")
                .setAutoComplete(true).setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        OptionMapping mods = event.getOption(Constants.COMBAT_MODIFIERS);
        OptionMapping planetOption = event.getOption(Constants.PLANET);
        OptionMapping extraRollsOption = event.getOption(Constants.COMBAT_EXTRA_ROLLS);

        if (tileOption == null) {
            return;
        }

        String unitHolderName = Constants.SPACE;
        if (planetOption != null) {
            unitHolderName = planetOption.getAsString();
        }

        // Get tile info
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = AddRemoveUnits.getTile(event, tileID, activeMap);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Tile " + tileOption.getAsString() + " not found");
            return;
        }
        TileModel tileModel = TileHelper.getAllTiles().get(tile.getTileID());

        String tileName = tile.getTilePath();
        tileName = tileName.substring(tileName.indexOf("_") + 1);
        tileName = tileName.substring(0, tileName.indexOf(".png"));
        tileName = " - " + tileName + "[" + tile.getTileID() + "]";
        StringBuilder sb = new StringBuilder();

        String userID = getUser().getId();
        Player player = activeMap.getPlayer(userID);
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        UnitHolder combatOnHolder = tile.getUnitHolders().get(unitHolderName);
        HashMap<UnitModel, Integer> unitsByQuantity = CombatHelper.GetUnitsInCombat(combatOnHolder, player);

        Player opponent = CombatHelper.GetOpponent(player, combatOnHolder, activeMap);

        List<NamedCombatModifierModel> modsParsed = new ArrayList<>();
        if (mods != null) {
            modsParsed = parseCustomUnitMods(mods.getAsString());
        }

        HashMap<String, Integer> extraRollsParsed = new HashMap<String, Integer>();
        if (extraRollsOption != null) {
            extraRollsParsed = parseUnits(extraRollsOption.getAsString());
        }

        List<UnitModel> unitsInCombat = new ArrayList<>(unitsByQuantity.keySet());
        modsParsed.addAll(CombatHelper.getAlwaysOnMods(player, opponent, unitsInCombat, tileModel, activeMap));

        String message = String.format("%s combat rolls for %s on %s: \n",
                StringUtils.capitalize(combatOnHolder.getName()), Helper.getFactionIconFromDiscord(player.getFaction()),
                tile.getPosition());
        message += rollUnits(unitsByQuantity, extraRollsParsed, modsParsed, player, opponent, activeMap);

        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        message = StringUtils.removeEnd(message, ";\n");
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        super.reply(event);
    }

    // TODO: Maybe use this as part of getAlwaysOnMods and custom mods initial
    // lists?
    private static Boolean inScopeForUnits(List<UnitModel> units, CombatModifierModel modifier) {
        for (UnitModel unit : units) {
            if (modifier.isInScopeForUnit(unit)) {
                return true;
            }
        }
        return false;
    }

    private String rollUnits(java.util.Map<UnitModel, Integer> units,
            HashMap<String, Integer> extraRolls, List<NamedCombatModifierModel> mods, Player player, Player opponent,
            Map map) {
        String result = "";

        // TODO: Not sure we need to check inScopeForUnits here - this shuold be done
        // earlier.
        // Display modifiers info
        List<NamedCombatModifierModel> alwaysOnModifiers = mods.stream()
                .filter(model -> model.getModifier().getPersistanceType() != null
                        && !model.getModifier().getPersistanceType().equals("CUSTOM"))
                .filter(model -> inScopeForUnits(new ArrayList<>(units.keySet()), model.getModifier()))
                .toList();
        List<NamedCombatModifierModel> customModifiers = mods.stream()
                .filter(model -> model.getModifier().getPersistanceType() != null
                        && model.getModifier().getPersistanceType().equals("CUSTOM"))
                .filter(model -> inScopeForUnits(new ArrayList<>(units.keySet()), model.getModifier()))
                .toList();
        result += getModifiersText("With automatic modifiers: \n", units, alwaysOnModifiers);
        result += getModifiersText("With custom modifiers: \n", units, customModifiers);

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
            int modifierToHit = CombatHelper.getTotalModifications(unit, mods, player, opponent, map);
            int extraRollsForUnit = 0;
            if (extraRolls.containsKey(unit.getAsyncId())) {
                extraRollsForUnit = extraRolls.get(unit.getAsyncId());
            }
            int numRollsPerUnit = unit.getCombatDieCount();
            int numRolls = (numOfUnit * numRollsPerUnit) + extraRollsForUnit;
            int[] resultRolls = new int[numRolls];
            for (int index = 0; index < numRolls; index++) {
                resultRolls[index] = randomRange(1, 10);
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

    private String getModifiersText(String prefixText, java.util.Map<UnitModel, Integer> units,
            List<NamedCombatModifierModel> modifiers) {
        String result = "";
        if (!modifiers.isEmpty()) {

            result += prefixText;
            ArrayList<String> modifierMessages = new ArrayList<String>();
            for (NamedCombatModifierModel namedModifier : modifiers) {
                CombatModifierModel mod = namedModifier.getModifier();
                String unitScope = mod.getScope(); // TODO: Move to helper at least, also i feel like this happens
                                                   // somewhere else.
                if (StringUtils.isNotBlank(unitScope)) {
                    Optional<UnitModel> unitScopeModel = units.keySet().stream()
                            .filter(unit -> unit.getAsyncId().equals(mod.getScope())).findFirst();
                    if (unitScopeModel.isPresent()) {
                        unitScope = Helper.getEmojiFromDiscord(unitScopeModel.get().getBaseType());
                    }
                } else {
                    unitScope = "all";
                }

                String plusPrefix = "+";
                Integer modifierValue = mod.getValue();
                if (modifierValue < 0) {
                    plusPrefix = "";
                }
                String modifierName = namedModifier.getName();
                if (StringUtils.isNotBlank(modifierName)) {
                    modifierMessages.add(modifierName);
                } else {
                    modifierMessages.add(String.format("%s%s for %s", plusPrefix, modifierValue, unitScope));
                }

            }
            result += String.join("\n", modifierMessages) + "\n";
        }
        return result;
    }

    // TODO: move to AliasHandler.resolveUnitModifactions
    private List<NamedCombatModifierModel> parseCustomUnitMods(String unitList) {
        List<NamedCombatModifierModel> resultList = new ArrayList<>();
        unitList = unitList.replace(", ", ",");
        StringTokenizer unitListTokenizer = new StringTokenizer(unitList, ",");
        while (unitListTokenizer.hasMoreTokens()) {
            String unitListToken = unitListTokenizer.nextToken();
            StringTokenizer unitInfoTokenizer = new StringTokenizer(unitListToken, " ");
            int count = 1;
            boolean numberIsSet = false;

            String unit = "";
            if (unitInfoTokenizer.hasMoreTokens()) {
                String ifNumber = unitInfoTokenizer.nextToken();
                try {
                    count = Integer.parseInt(ifNumber);
                    numberIsSet = true;
                } catch (Exception e) {
                    unit = AliasHandler.resolveUnit(ifNumber);
                }
            }
            if (unitInfoTokenizer.hasMoreTokens() && numberIsSet) {
                unit = AliasHandler.resolveUnit(unitInfoTokenizer.nextToken());
            }

            if (unit != null) {
                CombatModifierModel combatModifier = new CombatModifierModel();
                combatModifier.setValue(count);
                combatModifier.setScope(unit);
                combatModifier.setPersistanceType("CUSTOM");
                resultList.add(new NamedCombatModifierModel(combatModifier, ""));
            }
        }
        return resultList;
    }

    // TODO: Move to AliasHelper
    private HashMap<String, Integer> parseUnits(String unitList) {
        HashMap<String, Integer> resultList = new HashMap<String, Integer>();
        unitList = unitList.replace(", ", ",");
        StringTokenizer unitListTokenizer = new StringTokenizer(unitList, ",");
        while (unitListTokenizer.hasMoreTokens()) {
            String unitListToken = unitListTokenizer.nextToken();
            StringTokenizer unitInfoTokenizer = new StringTokenizer(unitListToken, " ");
            int count = 1;
            boolean numberIsSet = false;

            String unit = "";
            if (unitInfoTokenizer.hasMoreTokens()) {
                String ifNumber = unitInfoTokenizer.nextToken();
                try {
                    count = Integer.parseInt(ifNumber);
                    numberIsSet = true;
                } catch (Exception e) {
                    unit = AliasHandler.resolveUnit(ifNumber);
                }
            }
            if (unitInfoTokenizer.hasMoreTokens() && numberIsSet) {
                unit = AliasHandler.resolveUnit(unitInfoTokenizer.nextToken());
            }

            if (unit != null) {
                resultList.put(unit, count);
            }
        }
        return resultList;
    }

    private static int randomRange(int min, int max) {
        Random random = new Random();
        return random.nextInt(max - min + 1) + min;
    }
}