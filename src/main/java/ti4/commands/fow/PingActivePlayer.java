package ti4.commands.fow;

import java.util.Date;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PingActivePlayer extends FOWSubcommandData {

    public PingActivePlayer() {
        super(Constants.PING_ACTIVE_PLAYER, "Ping the active player in this game");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        String playerID = game.getActivePlayerID();
        if (playerID == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There is no active player right now.");
            return;
        }
        Player player = game.getPlayer(playerID);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There is no active player right now.");
            return;
        }
        Player playerOrig = Helper.getGamePlayer(game, player, event, null);
        long milliSinceLastPing = System.currentTimeMillis() - game.getLastActivePlayerPing().getTime();
        boolean samePlayer = false;
        if (playerOrig != null) {
            samePlayer = playerOrig.getUserID().equalsIgnoreCase(player.getUserID());
        }

        if (milliSinceLastPing < (1000 * 60 * 60 * 8) && !samePlayer) { //eight hours
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Active player was pinged recently. Try again later.");
        } else {
            String ping = player.getRepresentationUnfogged() + " this is a gentle reminder that it is your turn.";
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Active player has been pinged.");
                MessageHelper.sendPrivateMessageToPlayer(player, game, ping);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), ping);
            }
            game.setLastActivePlayerPing(new Date());
            GameSaveLoadManager.saveGame(game, "Auto Ping");
        }
        ButtonHelper.increasePingCounter(GameManager.getInstance().getGame("finreference"), player.getUserID());
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
    }
}
