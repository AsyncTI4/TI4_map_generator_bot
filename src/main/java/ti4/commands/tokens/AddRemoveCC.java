package ti4.commands.tokens;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.StringTokenizer;

abstract public class AddRemoveCC implements Command {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        MapManager mapManager = MapManager.getInstance();
        if (!mapManager.isUserWithActiveMap(userID)) {
            MessageHelper.replyToMessage(event, "Set your active map using: /set_map mapname");
        } else {
            String colorString = event.getOptions().get(0).getAsString().toLowerCase();
            colorString = colorString.replace(" ", "");
            StringTokenizer colorTokenizer = new StringTokenizer(colorString, ",");
            ArrayList<String> colors = new ArrayList<>();
            while (colorTokenizer.hasMoreTokens()){
                String color = colorTokenizer.nextToken();
                if (!colors.contains(color)) {
                    colors.add(color);
                    if (!Mapper.isColorValid(color)) {
                        MessageHelper.replyToMessage(event, "Color not valid: " + color);
                        return;
                    }
                }
            }
            String tileID = AliasHandler.resolveTile(event.getOptions().get(1).getAsString().toLowerCase());
            Map activeMap = mapManager.getUserActiveMap(userID);
            if (activeMap.isTileDuplicated(tileID)){
                MessageHelper.replyToMessage(event, "Duplicate tile name found, please use position coordinates");
                return;
            }
            Tile tile = activeMap.getTile(tileID);
            if (tile == null){
                tile = activeMap.getTileByPostion(tileID);
            }
            if (tile == null) {
                MessageHelper.replyToMessage(event, "Tile in map not found");
                return;
            }

            parsingForTile(event, colors, tile);
            MapSaveLoadManager.saveMap(activeMap);

            File file = GenerateMap.getInstance().saveImage(activeMap);
            MessageHelper.replyToMessage(event, file);
        }
    }

    abstract void parsingForTile(SlashCommandInteractionEvent event, ArrayList<String> color, Tile tile);
    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color: red, green etc.")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                                .setRequired(true))
        );
    }

    abstract protected String getActionDescription();


}
