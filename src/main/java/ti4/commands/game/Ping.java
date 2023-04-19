package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class Ping extends GameSubcommandData {

    public Ping() {
        super(Constants.PING, "Ping all players in the game");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Helper.fixGameChannelPermissions(event.getGuild(), getActiveMap());
        pingGame(event, getActiveMap());
    }

    public void pingGame(GenericInteractionCreateEvent event, Map activeMap) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Ping Game: " + Helper.getGamePing(event.getGuild(), activeMap));
    }
}