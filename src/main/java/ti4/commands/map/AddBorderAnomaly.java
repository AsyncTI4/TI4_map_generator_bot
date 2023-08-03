package ti4.commands.map;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.MapGenerator;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;
import ti4.model.BorderAnomalyModel;

import java.util.List;

import static ti4.model.BorderAnomalyModel.getBorderAnomalyTypeFromString;

public class AddBorderAnomaly implements Command {
    @Override
    public String getActionID() {
        return Constants.ADD_BORDER_ANOMALY;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(getActionID())) {
            return false;
        }
        String mapName = event.getOptions().get(0).getAsString();
        if (!MapManager.getInstance().getMapList().containsKey(mapName)) {
            MessageHelper.replyToMessage(event, "Game with such name: " + mapName + " does not exist.");
            return false;
        }
        Map activeMap = MapManager.getInstance().getMap(mapName);
        if (!activeMap.getTileMap().containsKey(event.getOption(Constants.PRIMARY_TILE, null, OptionMapping::getAsString))) {
            MessageHelper.replyToMessage(event, "Map does not contain that tile");
            return false;
        }
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tile = event.getOption(Constants.PRIMARY_TILE, null, OptionMapping::getAsString);
        Integer direction = event.getOption(Constants.PRIMARY_TILE_DIRECTION, null, OptionMapping::getAsInt);
        String anomalyTypeString = event.getOption(Constants.BORDER_TYPE, null, OptionMapping::getAsString);

        BorderAnomalyModel.BorderAnomalyType anomalyType = getBorderAnomalyTypeFromString(anomalyTypeString);

        if(!List.of(1,2,3,4,5,6).contains(direction)) {
            MessageHelper.replyToMessage(event, "Invalid direction value! Valid options are 1-6, corresponding to the edges clockwise from the top");
            return;
        }

        User user = event.getUser();
        Map activeMap = MapManager.getInstance().getUserActiveMap(user.getId());

        activeMap.addBorderAnomaly(tile, direction, anomalyType);
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(Commands.slash(getActionID(), "Add a border anomaly to a tile")
                .addOptions(
                        new OptionData(OptionType.STRING, Constants.PRIMARY_TILE, "Tile the border will be linked to").setRequired(true),
                        new OptionData(OptionType.INTEGER, Constants.PRIMARY_TILE_DIRECTION, "Side of the tile the anomaly will be on (clockwise from top 1-6)").setRequired(true),
                        new OptionData(OptionType.STRING, Constants.BORDER_TYPE, "Type of anomaly").setRequired(true)
                )
        );
    }
}
