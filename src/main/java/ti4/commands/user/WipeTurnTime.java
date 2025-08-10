package ti4.commands.user;

import static java.util.function.Predicate.not;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.executors.ExecutionLockManager;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.map.persistence.ManagedPlayer;
import ti4.message.MessageHelper;

class WipeTurnTime extends Subcommand {

    public WipeTurnTime() {
        super(Constants.WIPE_TURN_TIME, "Wipe your turn time in all games");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        ManagedPlayer managedPlayer = GameManager.getManagedPlayer(userId);
        if (managedPlayer == null) {
            return;
        }
        managedPlayer.getGames().stream()
                .filter(not(ManagedGame::isFowMode))
                .map(ManagedGame::getName)
                .distinct()
                .forEach(gameName -> ExecutionLockManager.wrapWithLockAndRelease(
                                gameName,
                                ExecutionLockManager.LockType.WRITE,
                                () -> wipeTurnTime(gameName, userId, event))
                        .run());

        MessageHelper.sendMessageToChannel(event.getChannel(), "Wiped all of your turn times");
    }

    private void wipeTurnTime(String gameName, String playerId, SlashCommandInteractionEvent event) {
        Game game = GameManager.getManagedGame(gameName).getGame();
        Player player = game.getPlayer(playerId);
        if (player != null) {
            player.setTotalTurnTime(0);
            player.setNumberOfTurns(0);
            GameManager.save(game, "Wiped turn time.");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), "Wiped all of your turn times");
    }
}
