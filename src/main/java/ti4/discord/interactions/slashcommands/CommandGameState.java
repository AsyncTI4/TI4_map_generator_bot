package ti4.discord.interactions.slashcommands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.logging.RollbarManager;
import ti4.service.event.EventAuditService;
import ti4.service.game.GameNameService;

record CommandGameState(boolean saveGame, boolean playerCommand) {

    private static final ThreadLocal<Game> game = new ThreadLocal<>();
    private static final ThreadLocal<Player> player = new ThreadLocal<>();

    void preExecute(SlashCommandInteractionEvent event) {
        String gameName = GameNameService.getGameName(event);
        if (!GameManager.isValid(gameName)) {
            throw new IllegalArgumentException("Invalid game name: " + gameName + " while attempting to run event "
                    + event.getName() + " in channel " + event.getChannel().getName());
        }
        Game game = GameManager.getManagedGame(gameName).getGame();
        CommandGameState.game.set(game);
        RollbarManager.put("game_name", game.getName());

        if (!playerCommand) {
            return;
        }
        var player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            throw new IllegalArgumentException("Unable to determine player while attempting to run event "
                    + event.getName() + " in channel " + event.getChannel().getName() + " for game " + gameName);
        }
        CommandGameState.player.set(player);
        RollbarManager.put("player_id", player.getUserID());
    }

    void postExecute(SlashCommandInteractionEvent event) {
        if (saveGame) {
            Game game = CommandGameState.game.get();
            GameManager.save(game, EventAuditService.getReason(event));
        }
        clear();
    }

    void clear() {
        game.remove();
        player.remove();
    }

    @NotNull
    public Game getGame() {
        return game.get();
    }

    @NotNull
    public Player getPlayer() {
        if (!playerCommand) {
            throw new IllegalStateException(
                    "CommandGameStateHelper cannot get player state because command was not set to be a player command. This is a bug.");
        }
        return player.get();
    }
}
