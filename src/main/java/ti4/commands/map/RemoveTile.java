package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.map.AddRemoveTile;
import ti4.generator.PositionMapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class RemoveTile extends AddRemoveTile {

    @Override
    public String getActionID() {
        return Constants.REMOVE_TILE;
    }

    @Override
    protected void tileAction(Tile tile, String position, Map userActiveMap) {
        userActiveMap.removeTile(position);
    }

    @Override
    protected Map tileParsing(SlashCommandInteractionEvent event, String userID, MapManager mapManager) {
        String position = event.getOptions().get(0).getAsString();
        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.replyToMessage(event, "Position tile not allowed");
            return null;
        }

        Map userActiveMap = mapManager.getUserActiveMap(userID);
        tileAction(null, position, userActiveMap);
        return userActiveMap;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile position on map")
                                .setRequired(true))

        );
    }

    @Override
    protected String getActionDescription() {
        return "Remove tile from map";
    }
}
