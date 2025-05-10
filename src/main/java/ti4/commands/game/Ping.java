package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.message.MessageHelper;

class Ping extends GameStateSubcommand {

    public Ping() {
        super(Constants.PING, "Ping all players in the game", false, false);
    }

    public void execute(SlashCommandInteractionEvent event) {
        var game = getGame();
        Helper.fixGameChannelPermissions(event.getGuild(), game);
        pingGame(event, game);
    }

    public void pingGame(GenericInteractionCreateEvent event, Game game) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Ping Game: " + game.getPing());
    }
}