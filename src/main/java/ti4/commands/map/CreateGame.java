package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CreateGame implements Command {


    @Override
    public String getActionID() {
        return Constants.CREATE_MAP;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(getActionID())) {
            return false;
        }
        String mapName = event.getOptions().get(0).getAsString();
        String regex = "^[a-zA-Z0-9]+$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(mapName);
        if (!matcher.matches()){
            MessageHelper.replyToMessage(event, "Map name can only contain a-z A-Z 0-9 symbols");
            return false;
        }
        if (MapManager.getInstance().getMapList().containsKey(mapName)) {
            MessageHelper.replyToMessage(event, "Map with such name exist already, choose different name");
            return false;
        }
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map map = new Map();
        String ownerID = event.getUser().getId();
        map.setOwnerID(ownerID);
        map.setOwnerName(event.getUser().getName());
        String mapName = event.getOptions().get(0).getAsString().toLowerCase();
        map.setName(mapName);

        MapManager mapManager = MapManager.getInstance();
        mapManager.addMap(map);
        boolean setMapSuccessful = mapManager.setMapForUser(ownerID, mapName);
        if (!setMapSuccessful) {
            MessageHelper.replyToMessage(event, "Could not assign active map " + mapName);
        }
        MessageHelper.replyToMessage(event, "Map created with name: " + mapName);
        MapSaveLoadManager.saveMap(map);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Shows selected map")
                        .addOptions(new OptionData(OptionType.STRING, Constants.MAP_NAME, "Map name")
                                .setRequired(true))
        );
    }
}
