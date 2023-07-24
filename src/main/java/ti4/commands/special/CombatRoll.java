package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

public class CombatRoll extends SpecialSubcommandData {
    public CombatRoll() {
        super("combat_roll", "Combat rolls for player's units on tile.");// Constants.COMBAT_ROLL
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        // if (activeMap.isFoWMode()) {
        // sendMessage("This is disabled for FoW for now.");
        // return;
        // }
        for (OptionMapping tileOption : event.getOptions()) {
            if (tileOption == null) {
                continue;
            }
            String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
            Tile tile = AddRemoveUnits.getTile(event, tileID, activeMap);
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Tile " + tileOption.getAsString() + " not found");
                continue;
            }
            Player player = activeMap.getPlayer(getUser().getId());
            player = Helper.getGamePlayer(activeMap, player, event, null);
            player = Helper.getPlayer(activeMap, player, event);
            if (player == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
                return;
            }

            java.util.Map<String, String> colorToId = Mapper.getColorToId();
            String tileName = tile.getTilePath();
            tileName = tileName.substring(tileName.indexOf("_") + 1);
            tileName = tileName.substring(0, tileName.indexOf(".png"));
            tileName = " - " + tileName + "[" + tile.getTileID() + "]";
            StringBuilder sb = new StringBuilder();
            StringBuilder sb_roll = new StringBuilder();

            String playerColor = player.getColor();
            String playerColorKey = getFactionColorId(activeMap, colorToId, playerColor, event);

            java.util.HashMap<UnitModel, Integer> unitsByQuantity = convertToUnitModels(player, tile.getUnitHolders());
            String results = rollUnits(unitsByQuantity, 0, "");

            MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
            String message = results;
            message = StringUtils.removeEnd(message, ";\n");
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
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

    private String rollUnits(java.util.Map<UnitModel, Integer> units, int modifier, String modifierScope) {
        StringBuilder result = new StringBuilder("Combat roll results: \n");

        // if (modifier != 0) {
        // if (modifierScope == null) {
        // modifierScope = "all units";
        // }
        // result.append("With modifier: ").append(modifier).append(" applied to
        // ").append(modifierScope).append("\n");
        // }

        int totalHits = 0;
        for (java.util.Map.Entry<UnitModel, Integer> entry : units.entrySet()) {
            UnitModel unit = entry.getKey();
            int numOfUnit = entry.getValue();

            int toHit = unit.getCombatHitsOn();
            int[] resultRolls = new int[numOfUnit];
            for (int index = 0; index < numOfUnit; index++) {
                resultRolls[index] = randomRange(1, 10);
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
                        return roll >= toHit;
                    })
                    .toArray();

            String rollsSuffix = "";
            totalHits += hitRolls.length;
            if (hitRolls.length > 1) {
                rollsSuffix = "s";
            }

            var unitEmoji = Helper.getEmojiFromDiscord(unit.getBaseType());
            result.append(numOfUnit).append(" ").append(unitEmoji).append(" ").append(quantitySuffix)
                    .append(" (>= ").append(toHit).append("): (").append(Arrays.toString(resultRolls)).append(") ")
                    .append(hitRolls.length).append(" hit").append(rollsSuffix).append("\n");
        }

        result.append("Total hits: ").append(totalHits).append("\n");
        return result.toString();
    }

    private static int randomRange(int min, int max) {
        Random random = new Random();
        return random.nextInt(max - min + 1) + min;
    }
}