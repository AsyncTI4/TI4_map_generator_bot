package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.metadata.AutoPingMetadataManager;

class PingActivePlayer extends GameStateSubcommand {

    public PingActivePlayer() {
        super(Constants.PING_ACTIVE_PLAYER, "Ping the active player in this game", true, true);
    }

    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

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

        Player playerThatRanCommand = getPlayer();
        boolean samePlayer = playerThatRanCommand.getUserID().equalsIgnoreCase(player.getUserID());

        long latestPingMilliseconds = 0;
        AutoPingMetadataManager.AutoPing autoPing = AutoPingMetadataManager.getLatestAutoPing(game.getName());
        if (autoPing != null) {
            latestPingMilliseconds = autoPing.lastPingTimeEpochMilliseconds();
        } else if (game.getLastActivePlayerChange() != null) {
            latestPingMilliseconds = game.getLastActivePlayerChange().getTime();
        }

        long milliSinceLastPing = System.currentTimeMillis() - latestPingMilliseconds;
        if (!game.getPlayersWithGMRole().contains(playerThatRanCommand)
                && milliSinceLastPing < (1000 * 60 * 60 * 8)
                && !samePlayer) { // eight hours
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Active player was pinged recently. Try again later.");
        } else {
            String ping = player.getRepresentationUnfogged() + " this is a gentle reminder that it is your turn.";
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Active player has been pinged.");
                MessageHelper.sendPrivateMessageToPlayer(player, game, ping);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), ping);
            }
            AutoPingMetadataManager.addPing(game.getName());
        }
    }
}
