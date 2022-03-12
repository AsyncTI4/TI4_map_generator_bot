package ti4.commands.map;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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

public class SetMap implements Command {

    @Override
    public String getActionID() {
        return Constants.SET_MAP;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(getActionID())) {
            return false;
        }
        String mapName = event.getOptions().get(0).getAsString();
        if (!MapManager.getInstance().getMapList().containsKey(mapName)) {
            MessageHelper.replyToMessage(event, "Map with such name: "+mapName+ " does not exists, use /list_maps");
            return false;
        }
        String userID = event.getUser().getId();
        Map map = MapManager.getInstance().getMap(mapName);
        if (map.isMapOpen()){
            return true;
        }
        if (MapGenerator.userID.equals(userID)){
            return true;
        }
        if (!map.getPlayers().containsKey(userID) && !userID.equals(map.getOwnerID())){
            MessageHelper.replyToMessage(event, "Your are not a player of selected map.");
            return false;
        }
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        String mapName = event.getOptions().get(0).getAsString().toLowerCase();
        boolean setMapSuccessful = MapManager.getInstance().setMapForUser(userID, mapName);
        if (!setMapSuccessful) {
            MessageHelper.replyToMessage(event, "Could not assign active map " + mapName);
        } else {
            MessageHelper.replyToMessage(event, "Active Map set: " + mapName);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Set map as active")
                        .addOptions(new OptionData(OptionType.STRING, Constants.MAP_NAME, "Map name to be set as active")
                                .setRequired(true))

        );
    }
}
