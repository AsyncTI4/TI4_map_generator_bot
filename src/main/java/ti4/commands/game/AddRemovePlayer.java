package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import ti4.MapGenerator;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

abstract public class AddRemovePlayer extends GameSubcommandData {

    public AddRemovePlayer(@NotNull String name, @NotNull String description) {
        super(name, description);
        addOptions(new OptionData(OptionType.STRING, Constants.MAP_NAME, "Map name").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player @playerName").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mapName = event.getOptions().get(0).getAsString();
        if (!MapManager.getInstance().getMapList().containsKey(mapName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Map with such name does not exists, use /list_maps");
            return;
        }
        User callerUser = event.getUser();

        MapManager mapManager = MapManager.getInstance();
        Map map = mapManager.getMap(mapName);
        if (!map.getOwnerID().equals(callerUser.getId()) && !event.getUser().getId().equals(MapGenerator.userID)){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Just Game/Map owner can add/remove players.");
            return;
        }
        if (!map.isMapOpen()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Map is not open. Can add/remove only in open map.");
            return;
        }

        User user = event.getOptions().get(1).getAsUser();
        action(map, user);
        MapSaveLoadManager.saveMap(map);
        MessageHelper.replyToMessage(event, getResponseMessage(map, user));
    }
    abstract protected String getResponseMessage(Map map, User user);

    abstract protected void action(Map map, User user);
}