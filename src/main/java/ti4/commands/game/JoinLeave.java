package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

abstract public class JoinLeave extends GameSubcommandData {

    public JoinLeave(@NotNull String name, @NotNull String description) {
        super(name, description);
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mapName = event.getOptions().get(0).getAsString();
        if (!MapManager.getInstance().getMapList().containsKey(mapName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Game with such name does not exists, use /list_games");
            return;
        }

        MapManager mapManager = MapManager.getInstance();
        Map map = mapManager.getMap(mapName);
        if (!map.isMapOpen()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Game is not open. Can join/leave only open game.");
            return;
        }
        User user = event.getUser();
        action(map, user);
        MapSaveLoadManager.saveMap(map);
        MessageHelper.replyToMessage(event, getResponseMessage(map, user));
    }
    abstract protected String getResponseMessage(Map map, User user);

    abstract protected void action(Map map, User user);
}