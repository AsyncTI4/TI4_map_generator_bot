package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

abstract public class JoinLeave extends GameSubcommandData {

    public JoinLeave(@NotNull String name, @NotNull String description) {
        super(name, description);
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        if (!activeMap.isMapOpen()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Game is not open. Can join/leave only open game.");
            return;
        }
        User user = event.getUser();
        action(activeMap, user);
        Helper.fixGameChannelPermissions(event.getGuild(), activeMap);
        MapSaveLoadManager.saveMap(activeMap, event);
        MessageHelper.replyToMessage(event, getResponseMessage(activeMap, user));
    }
    abstract protected String getResponseMessage(Map activeMap, User user);

    abstract protected void action(Map activeMap, User user);
}