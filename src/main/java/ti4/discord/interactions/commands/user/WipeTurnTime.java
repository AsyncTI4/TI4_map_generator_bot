package ti4.discord.interactions.commands.user;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.game.persistence.ManagedPlayer;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class WipeTurnTime extends Subcommand {

    WipeTurnTime() {
        super(Constants.WIPE_TURN_TIME, "Wipe your turn time in all games");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        ManagedPlayer managedPlayer = GameManager.getManagedPlayer(userId);
        if (managedPlayer == null) {
            return;
        }

        List<String> gameNames =
                managedPlayer.getGames().stream().map(ManagedGame::getName).toList();
        ConsumeGameUtility.consumeGames(gameNames, game -> wipeTurnTime(game, userId), ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Wiped all of your turn times");
    }

    private void wipeTurnTime(Game game, String playerId) {
        Player player = game.getPlayer(playerId);
        if (player != null) {
            player.setTotalTurnTime(0);
            player.setNumberOfTurns(0);
            player.setExpectedHitsTimes10(0);
            player.setActualHits(0);
            GameManager.save(game, "Wiped turn time.");
        }
    }
}
