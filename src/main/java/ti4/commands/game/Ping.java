package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.UserGameContextManager;
import ti4.message.MessageHelper;

public class Ping extends GameSubcommandData {

    public Ping() {
        super(Constants.PING, "Ping all players in the game");
    }

    public void execute(SlashCommandInteractionEvent event) {
        String activeGameName = UserGameContextManager.getContextGame(event.getUser().getId());
        Helper.fixGameChannelPermissions(event.getGuild(), GameManager.getManagedGame(activeGameName));
        pingGame(event, getActiveGame());
    }

    public void pingGame(GenericInteractionCreateEvent event, Game game) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Ping Game: " + game.getPing());
    }
}