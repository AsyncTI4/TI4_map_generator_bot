package ti4.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapStatus;
import ti4.message.MessageHelper;

public class SetMapStatus implements Command {

    @Override
    public String getActionID() {
        return Constants.SET_MAP_STATUS;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(getActionID())) {
            return false;
        }
        String mapName = event.getOptions().get(0).getAsString();
        if (!MapManager.getInstance().getMapList().containsKey(mapName)) {
            MessageHelper.replyToMessage(event, "Map with such name does not exists, use /list_maps");
            return false;
        }

        String userID = event.getUser().getId();
        MapManager mapManager = MapManager.getInstance();
        Map mapToChangeStatusFor = mapManager.getMap(mapName);
        if (!mapToChangeStatusFor.getOwnerID().equals(userID)) {
            MessageHelper.replyToMessage(event, "Not Authorized Map Status change attempt");
        }
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mapName = event.getOptions().get(0).getAsString().toLowerCase();
        String status = event.getOptions().get(1).getAsString().toLowerCase();

        MapManager mapManager = MapManager.getInstance();
        Map mapToChangeStatusFor = mapManager.getMap(mapName);
        MapStatus mapStatus = MapStatus.valueOf(status);
        if (mapStatus == MapStatus.open) {
            mapToChangeStatusFor.setMapStatus(MapStatus.open);
        } else if (mapStatus == MapStatus.locked) {
            mapToChangeStatusFor.setMapStatus(MapStatus.locked);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Set map status")
                        .addOptions(new OptionData(OptionType.STRING, Constants.MAP_NAME, "Map name to be set as active")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.MAP_STATUS, "Map status: open, locked")
                                .setRequired(true))

        );
    }
}
