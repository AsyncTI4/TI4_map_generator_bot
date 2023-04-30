package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class SystemInfo extends SpecialSubcommandData {
    public SystemInfo() {
        super(Constants.SYSTEM_INFO, "Info for system (all units)");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_2, "System/Tile name").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_3, "System/Tile name").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_4, "System/Tile name").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_5, "System/Tile name").setRequired(false).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        for (OptionMapping tileOption : event.getOptions()) {
            if (tileOption == null){
                continue;
            }
            String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
            Tile tile = new AddUnits().getTile(event, tileID, activeMap);
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Tile " + tileOption.getAsString() + " not found");
                continue;
            }
            String tileName = tile.getTilePath();
            tileName = tileName.substring(tileName.indexOf("_") + 1);
            tileName = tileName.substring(0, tileName.indexOf(".png"));
            tileName = " - " + tileName + "[" + tile.getTileID() + "]";
            StringBuilder sb = new StringBuilder();
            sb.append("__**Tile: ").append(tile.getPosition()).append(tileName).append("**__\n");
            java.util.Map<String, String> unitRepresentation = Mapper.getUnits();
            HashMap<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
            java.util.Map<String, String> colorToId = Mapper.getColorToId();
            Boolean privateGame = FoWHelper.isPrivateGame(activeMap, event);
            for (java.util.Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                String name = entry.getKey();
                String representation = planetRepresentations.get(name);
                if (representation == null){
                    representation = name;
                }
                UnitHolder unitHolder = entry.getValue();
                if (unitHolder instanceof Planet planet) {
                    sb.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(representation, activeMap));
                    sb.append(" Resources: ").append(planet.getResources()).append("/").append(planet.getInfluence());

                    //commented as not all planets get traits still
    //                sb.append(" Trait: ");
    //                ArrayList<String> planetType = planet.getPlanetType();
    //                for (String type : planet.getPlanetType()) {
    //                    sb.append(type).append(" ");
    //                }
                } else {
                    sb.append(representation);
                }
                sb.append("\n");
                boolean hasCC = false;
                for (String cc : unitHolder.getCCList()) {
                    if (!hasCC){
                        sb.append("Command Counters: ");
                        hasCC = true;
                    }
                    addtFactionIcon(activeMap, sb, colorToId, cc, privateGame);
                }
                if (hasCC) {
                    sb.append("\n");
                }
                boolean hasControl = false;
                for (String control : unitHolder.getControlList()) {
                    if (!hasControl){
                        sb.append("Control Counters: ");
                        hasControl = true;
                    }
                    addtFactionIcon(activeMap, sb, colorToId, control, privateGame);
                }
                if (hasControl) {
                    sb.append("\n");
                }
                boolean hasToken = false;
                java.util.Map<String, String> tokensToName = Mapper.getTokensToName();
                for (String token : unitHolder.getTokenList()) {
                    if (!hasToken){
                        sb.append("Tokens: ");
                        hasToken = true;
                    }
                    for (java.util.Map.Entry<String, String> entry_ : tokensToName.entrySet()) {
                        String key = entry_.getKey();
                        String value = entry_.getValue();
                        if (token.contains(key)){
                                sb.append(value).append(" ");

                            }
                        }
                }
                if (hasToken) {
                    sb.append("\n");
                }

                HashMap<String, Integer> units = unitHolder.getUnits();
                for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {

                    String key = unitEntry.getKey();

                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey)) {
                            addtFactionIcon(activeMap, sb, colorToId, key, privateGame);
                            sb.append(unitRepresentation.get(unitRepresentationKey)).append(": ").append(unitEntry.getValue()).append("\n");
                        }
                    }
                }
                sb.append("----------\n");
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        }
    }

    private static void addtFactionIcon(Map activeMap, StringBuilder sb, java.util.Map<String, String> colorToId, String key, Boolean privateGame) {

        for (java.util.Map.Entry<String, String> colorEntry : colorToId.entrySet()) {
            String colorKey = colorEntry.getKey();
            String color = colorEntry.getValue();
            if (key.contains(colorKey)){
                for (Player player_ : activeMap.getPlayers().values()) {
                    if (Objects.equals(player_.getColor(), color)) {
                        if (privateGame != null && privateGame) {
                            sb.append(" (").append(color).append(") ");
                        } else {
                            sb.append(Helper.getFactionIconFromDiscord(player_.getFaction())).append(" ").append(" (").append(color).append(") ");
                        }
                    }
                }
            }
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        super.reply(event);
    }
}
