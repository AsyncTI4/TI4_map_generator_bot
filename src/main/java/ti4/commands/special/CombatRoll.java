package ti4.commands.special;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import lombok.experimental.var;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

public class CombatRoll extends SpecialSubcommandData {
    public CombatRoll() {
        super("combat_roll", "Combat rolls for player's units on tile.");// Constants.COMBAT_ROLL
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, "mods",
                "+/- <unit type>. Eg -1 all, +2 mech, +1 fighter").setRequired(false));
        // addOptions(new OptionData(OptionType.STRING, "mod1for", // add constant
        // "Comma separated list of '{count} unit' Example: Dread, 2 Warsuns, 4
        // Infantry").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        // if (activeMap.isFoWMode()) {
        // sendMessage("This is disabled for FoW for now.");
        // return;
        // }
        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        OptionMapping mods = event.getOption("mods");

        if (tileOption == null) {
            return;
        }
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = AddRemoveUnits.getTile(event, tileID, activeMap);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Tile " + tileOption.getAsString() + " not found");
            return;
        }
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        // java.util.Map<String, String> colorToId = Mapper.getColorToId();
        String tileName = tile.getTilePath();
        tileName = tileName.substring(tileName.indexOf("_") + 1);
        tileName = tileName.substring(0, tileName.indexOf(".png"));
        tileName = " - " + tileName + "[" + tile.getTileID() + "]";
        StringBuilder sb = new StringBuilder();

        // String playerColor = player.getColor();
        // String playerColorKey = getFactionColorId(activeMap, colorToId, playerColor,
        // event);

        java.util.HashMap<UnitModel, Integer> unitsByQuantity = convertToUnitModels(player, tile.getUnitHolders());
        HashMap<String, Integer> modsParsed = parseUnits(mods.getAsString());
        String results = rollUnits(unitsByQuantity, modsParsed);

        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        String message = results;
        message = StringUtils.removeEnd(message, ";\n");
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }

    private UnitModel convertFromUnitHolderToModel(String unitHolderString, Player player) {
        var unitRepresentation = Mapper.getUnitImageSuffixes();
        var filteredList = unitRepresentation
                .entrySet()
                .stream()
                .filter(rep -> unitHolderString.endsWith(rep.getKey())
                        && unitHolderString.startsWith(player
                                .getColor()))
                .collect(Collectors.toList());

        UnitModel result = null;
        String modelName = null;
        if (!filteredList.isEmpty()) {
            modelName = filteredList.get(0).getValue();
            result = player.getUnitByID(modelName.toLowerCase());
        }
        return result;
    }

    private java.util.HashMap<UnitModel, Integer> convertToUnitModels(Player player,
            java.util.HashMap<String, UnitHolder> unitHolders) {
        // var spaceUnits = unitHolders["space"]
        java.util.Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
        String playerColorKey = player.getColor();

        var spaceUnitHolder = unitHolders.get("space");// unitHolders.entrySet().stream().filter().collect(Collectors.toMap(Entry::getKey,
                                                       // Entry::getValue));
        java.util.HashMap<UnitModel, Integer> unitModels = new HashMap<>();

        HashMap<String, Integer> units = spaceUnitHolder.getUnits();
        String playerColor = player.getColor();
        // String playerColorKey = getFactionColorId(activeMap, colorToId, playerColor,
        // event);
        for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {

            String unitType = unitEntry.getKey();
            Integer quantity = unitEntry.getValue();

            UnitModel unitModel = convertFromUnitHolderToModel(unitType, player);
            if (unitModel != null) {
                unitModels.put(unitModel, quantity);
            }

        }
        return unitModels;
    }

    private String getFactionColorId(Map activeMap, java.util.Map<String, String> colorToId, String playerColor,
            SlashCommandInteractionEvent event) {

        String colorKeyValue = "";
        for (java.util.Map.Entry<String, String> colorEntry : colorToId.entrySet()) {
            String colorKey = colorEntry.getKey();
            String color = colorEntry.getValue();
            if (playerColor.equals(color)) {
                colorKeyValue = colorKey;
            }
        }
        return colorKeyValue;
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        super.reply(event);
    }

    private String rollUnits(java.util.Map<UnitModel, Integer> units, HashMap<String, Integer> mods) {
        String result = "Combat rolls: \n";
        var unitsWithModifiers = units.keySet().stream().filter(unit -> mods.containsKey(unit.getAsyncId()))
                .collect(Collectors.toList());
        if (!mods.isEmpty()) {

            result += "With modifiers: \n";

            if (mods.containsKey("all")) {
                String plusPrefix = "+";
                Integer modifierValue = mods.get("all");
                if (modifierValue < 0) {
                    plusPrefix = "";
                }
                result += String.format("%s%s applied to all\n", plusPrefix, modifierValue);
            }
            for (UnitModel unit : unitsWithModifiers) {
                String plusPrefix = "+";
                Integer modifierValue = mods.get(unit.getAsyncId());
                String unitAsnycEmoji = Helper.getEmojiFromDiscord(unit.getBaseType());
                if (modifierValue < 0) {
                    plusPrefix = "";
                }
                result += String.format("%s%s applied to %s\n", plusPrefix, modifierValue, unitAsnycEmoji);
            }

        }

        int totalHits = 0;

        //
        for (java.util.Map.Entry<UnitModel, Integer> entry : units.entrySet()) {
            UnitModel unit = entry.getKey();
            int numOfUnit = entry.getValue();

            int toHit = unit.getCombatHitsOn();
            int modifierToHit = getTotalModifications(unit, mods);
            int numRollsPerUnit = unit.getCombatDieCount();
            int[] resultRolls = new int[numOfUnit * numRollsPerUnit];
            for (int index = 0; index < numOfUnit; index++) {
                for (int rollIndex = 0; rollIndex < numRollsPerUnit; ++rollIndex) {
                    resultRolls[index] = randomRange(1, 10);
                }
            }

            String quantitySuffix = "";
            if (numOfUnit > 1) {
                quantitySuffix = "s";
            }

            int[] hitRolls = Arrays.stream(resultRolls)
                    .filter(roll -> {
                        // if (inScope(roll, index, modifierScope)) {
                        // toHit += modifier;
                        // }
                        return roll >= toHit + modifierToHit;
                    })
                    .toArray();

            String rollsSuffix = "";
            totalHits += hitRolls.length;
            if (hitRolls.length > 1) {
                rollsSuffix = "s";
            }

            String unitTypeHitsInfo = String.format("(hits on %s)", toHit);
            if (unit.getCombatDieCount() > 1) {
                unitTypeHitsInfo = String.format("(%s rolls, hits on %s)", unit.getCombatDieCount(), toHit);
            }
            if (modifierToHit != 0) {
                String modifierToHitString = Integer.toString(modifierToHit);
                if (modifierToHit > 0) {
                    modifierToHitString = "+" + modifierToHitString;
                }
                unitTypeHitsInfo = String.format("(hits on %s, with %s mods)", toHit, modifierToHitString);
                if (unit.getCombatDieCount() > 1) {
                    unitTypeHitsInfo = String.format("(%s rolls, hits on %s, with %s mods)", unit.getCombatDieCount(),
                            toHit, modifierToHitString);
                }
            }

            var unitEmoji = Helper.getEmojiFromDiscord(unit.getBaseType());
            result += String.format("%s %s %s %s - %s hit%s\n", numOfUnit, unitEmoji, unitTypeHitsInfo,
                    Arrays.toString(resultRolls), hitRolls.length, rollsSuffix);
        }

        result += String.format("Total hits %s\n", totalHits);
        return result.toString();
    }

    private Integer getTotalModifications(UnitModel unit, HashMap<String, Integer> mods) {
        Integer modValue = 0;
        if (mods.containsKey("all")) {
            modValue += mods.get(unit.getAsyncId());
        }
        if (mods.containsKey(unit.getAsyncId())) {
            modValue += mods.get(unit.getAsyncId());
        }
        return modValue;
    }

    private HashMap<String, Integer> parseUnits(String unitList) {
        HashMap<String, Integer> resultList = new HashMap<String, Integer>();
        unitList = unitList.replace(", ", ",");
        StringTokenizer unitListTokenizer = new StringTokenizer(unitList, ",");
        while (unitListTokenizer.hasMoreTokens()) {
            String unitListToken = unitListTokenizer.nextToken();
            StringTokenizer unitInfoTokenizer = new StringTokenizer(unitListToken, " ");

            int tokenCount = unitInfoTokenizer.countTokens();
            if (tokenCount > 3) {
                // MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(),
                // "Warning: Unit list should have a maximum of 3 parts `{count} {unit}
                // {planet}` - `" + unitListToken + "` has " + tokenCount + " parts. There may
                // be errors.");
            }

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

            // color = recheckColorForUnit(unit, color, event);

            // String unitID = Mapper.getUnitID(unit, color);
            // String unitPath = Tile.getUnitPath(unitID);
            if (unit != null) {
                resultList.put(unit, count);
            } else {
                // MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), "Unit:
                // `" + unit + "` is not valid and not supported. Please redo this part: `" +
                // unitListToken + "`");
                // continue;
            }
            // if (unitInfoTokenizer.hasMoreTokens()) {
            // String planetToken = unitInfoTokenizer.nextToken();
            // planetName = AliasHandler.resolvePlanet(planetToken);
            // // if (!Mapper.isValidPlanet(planetName)) {
            // // MessageHelper.sendMessageToChannel(event.getChannel(), "Planet: `" +
            // planetToken + "` is not valid and not supported. Please redo this part: `" +
            // unitListToken + "`");
            // // continue;
            // // }
            // }

            // planetName = getPlanet(event, tile, planetName);
            // unitAction(event, tile, count, planetName, unitID, color);

            // addPlanetToPlayArea(event, tile, planetName, activeMap);
        }
        return resultList;
    }

    // private string inScope(int rollValue, )

    private static int randomRange(int min, int max) {
        Random random = new Random();
        return random.nextInt(max - min + 1) + min;
    }
}