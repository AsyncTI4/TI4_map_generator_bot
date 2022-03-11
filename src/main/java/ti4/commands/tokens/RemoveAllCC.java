package ti4.commands.tokens;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.GenerateMap;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;

public class RemoveAllCC implements Command {

    void parsingForTile(SlashCommandInteractionEvent event, Map map) {
        Collection<Tile> tileList = map.getTileMap().values();
        for (Tile tile : tileList) {
            tile.removeAllCC();
        }
    }

    public String getActionID() {
        return Constants.REMOVE_ALL_CC;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        MapManager mapManager = MapManager.getInstance();
        if (!mapManager.isUserWithActiveMap(userID)) {
            MessageHelper.replyToMessage(event, "Set your active map using: /set_map mapName");
        } else {
            Map activeMap = mapManager.getUserActiveMap(userID);
            parsingForTile(event, activeMap);
            MapSaveLoadManager.saveMap(activeMap);
            File file = GenerateMap.getInstance().saveImage(activeMap);
            MessageHelper.replyToMessage(event, file);
        }
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Remove all cc from entire map")
                        .addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES to confirm")
                                .setRequired(true))
        );
    }
}
