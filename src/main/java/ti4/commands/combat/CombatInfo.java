package ti4.commands.combat;

import java.util.Map;
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

import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;

public class CombatInfo extends CombatSubcommandData {
    public CombatInfo() {
        super(Constants.COMBAT_INFO, "Combat info for system");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        // if (activeMap.isFoWMode()) {
        //     sendMessage("This is disabled for FoW for now.");
        //     return;
        // }
        for (OptionMapping tileOption : event.getOptions()) {
            if (tileOption == null){
                continue;
            }
            String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
            Tile tile = AddRemoveUnits.getTile(event, tileID, activeGame);
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Tile " + tileOption.getAsString() + " not found");
                continue;
            }
            Player player = activeGame.getPlayer(getUser().getId());
            player = Helper.getGamePlayer(activeGame, player, event, null);
            player = Helper.getPlayer(activeGame, player, event);
            if (player == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
                return;
            }
            Map<String, String> colorToId = Mapper.getColorToId();
            String tileName = tile.getTilePath();
            tileName = tileName.substring(tileName.indexOf("_") + 1);
            tileName = tileName.substring(0, tileName.indexOf(".png"));
            tileName = " - " + tileName + "[" + tile.getTileID() + "]";
            StringBuilder sb = new StringBuilder();
            StringBuilder sb_roll = new StringBuilder();
            sb_roll.append("/roll roll_command:");
            sb.append("__**Tile: ").append(tile.getPosition()).append(tileName).append("**__\n");
            Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
            Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
            Boolean privateGame = FoWHelper.isPrivateGame(activeGame, event);
            String baseRoll = "d10hv";
            String playerColor = player.getColor();
            String playerColorKey = getFactionColorId(activeGame, colorToId, playerColor, event);
            sb.append("Possible roll command for combat - modifiers, upgrades, or faction specific units may change these\n");
            sb.append("Please verify the below command before copy/paste/running it - this is **BETA/WIP**\n");
            for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                String name = entry.getKey();
                UnitHolder unitHolder = entry.getValue();

                HashMap<String, Integer> units = unitHolder.getUnits();
                for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {

                    String key = unitEntry.getKey();

                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        String combatNum = "10";
                        Integer diceRolls = unitEntry.getValue();
                        String unitType = unitRepresentation.get(unitRepresentationKey);
                        if (key.endsWith(unitRepresentationKey) && key.startsWith(playerColorKey)) {
                            if (unitType.contains("War Sun")) {
                                combatNum = "3";
                                diceRolls = diceRolls * 3;
                            }
                            else if (unitType.contains("Dreadnought")) {
                                combatNum = "5";
                            }
                            else if (unitType.contains("Flagship")) {
                                combatNum = "7";
                                diceRolls = diceRolls * 2;
                            }
                            else if (unitType.contains("Carrier")) {
                                combatNum = "9";
                            }
                            else if (unitType.contains("Destroyer")) {
                                combatNum = "9";
                            }
                            else if (unitType.contains("Cruiser")) {
                                combatNum = "7";
                            }
                            else if (unitType.contains("Fighter")) {
                                combatNum = "9";
                            }
                            if (!"10".equals(combatNum)) {
                                sb_roll.append(diceRolls).append(baseRoll).append(combatNum).append("?");
                                sb_roll.append(unitEntry.getValue()).append(" ").append(playerColor).append(" ").append(unitType).append("(s);\n");
                            }
                        }
                    }
                }
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
            String message = sb_roll.toString();
            message = StringUtils.removeEnd(message, ";\n");
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
    }

    private String getFactionColorId(Game activeGame, Map<String, String> colorToId, String playerColor, SlashCommandInteractionEvent event) {

        String colorKeyValue = "";
        for (Map.Entry<String, String> colorEntry : colorToId.entrySet()) {
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
}
