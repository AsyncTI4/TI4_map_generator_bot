package ti4.commands.map;

import net.dv8tion.jda.api.entities.User;
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

abstract public class JoinLeave implements Command {

    abstract protected String getActionDescription();

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(getActionID())) {
            return false;
        }
        String mapName = event.getOptions().get(0).getAsString();
        if (!MapManager.getInstance().getMapList().containsKey(mapName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Map with such name does not exists, use /list_maps");
            return false;
        }

        MapManager mapManager = MapManager.getInstance();
        Map map = mapManager.getMap(mapName);
        if (!map.isMapOpen()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Map is not open. Can leave only open map.");
        }
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mapName = event.getOptions().get(0).getAsString();
        MapManager mapManager = MapManager.getInstance();
        Map map = mapManager.getMap(mapName);
        if (!map.isMapOpen()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Map is not open. Can leave only open map.");
            return;
        }
        User user = event.getUser();
        action(map, user);
        MapSaveLoadManager.saveMap(map);
        MessageHelper.replyToMessage(event, getResponseMessage(map));
    }
    abstract protected String getResponseMessage(Map map);

    abstract protected void action(Map map, User user);


    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.MAP_NAME, "Map name")
                                .setRequired(true))

        );
    }
}