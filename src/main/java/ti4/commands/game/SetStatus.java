package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

public class SetStatus extends GameSubcommandData{

    public SetStatus() {
        super(Constants.SET_GAME_STATUS, "Game information:");
       addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name to be set as active").setRequired(true))
        .addOptions(new OptionData(OptionType.STRING, Constants.GAME_STATUS, "Game status: open, locked").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping mapNameOption = event.getOption(Constants.GAME_NAME);
        OptionMapping mapStatusOption = event.getOption(Constants.GAME_STATUS);
        if (mapNameOption == null || mapStatusOption == null){
            MessageHelper.replyToMessage(event, "Not all fields specified.");
            return;
        }
        String mapName = mapNameOption.getAsString().toLowerCase();
        String status = mapStatusOption.getAsString().toLowerCase();

        MapManager mapManager = MapManager.getInstance();
        Map mapToChangeStatusFor = mapManager.getMap(mapName);

        Map map = mapManager.getMap(mapName);
        if (!map.getOwnerID().equals(event.getUser().getId()) && !event.getUser().getId().equals(MapGenerator.userID)){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Just Game/Map owner can add/remove players.");
            return;
        }

        try {
            MapStatus mapStatus = MapStatus.valueOf(status);
            if (mapStatus == MapStatus.open) {
                mapToChangeStatusFor.setMapStatus(MapStatus.open);
            } else if (mapStatus == MapStatus.locked) {
                mapToChangeStatusFor.setMapStatus(MapStatus.locked);
            }
        } catch (Exception e){
            MessageHelper.replyToMessage(event, "Map: " + mapName + " status was not changed, as invalid status entered.");
            return;
        }
        MapSaveLoadManager.saveMap(mapToChangeStatusFor);
        MessageHelper.replyToMessage(event, "Map: "+ mapName +" status changed to: " + mapToChangeStatusFor.getMapStatus());
    }
}
