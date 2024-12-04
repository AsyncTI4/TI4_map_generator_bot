package ti4.commands2;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.service.SusSlashCommandService;

class CommandGameState {

    private final boolean saveGame;
    private final boolean isPlayerCommand;
    private final ThreadLocal<Game> game = new ThreadLocal<>();
    private final ThreadLocal<Player> player = new ThreadLocal<>();

    public CommandGameState(boolean saveGame, boolean isPlayerCommand) {
        this.saveGame = saveGame;
        this.isPlayerCommand = isPlayerCommand;
    }

    public void preExecute(SlashCommandInteractionEvent event) {
        String gameName = CommandHelper.getGameName(event);
        if (!GameManager.isValidGame(gameName)) {
            throw new IllegalArgumentException("Invalid game name: " + gameName + " while attempting to run event " + event.getName() +
                    " in channel " + event.getChannel().getName());
        }
        Game game = GameManager.getGame(gameName);
        game.incrementSpecificSlashCommandCount(event.getFullCommandName());  // TODO: This only works for commands that save...
        this.game.set(game);

        Member member = event.getMember();
        if (member != null) {
            String commandText = "```fix\n" + member.getEffectiveName() + " used " + event.getCommandString() + "\n```";
            event.getChannel().sendMessage(commandText).queue(message -> {
                BotLogger.logSlashCommand(event, message);
                SusSlashCommandService.checkIfShouldReportSusSlashCommand(event, event.getUser().getId(), message, GameManager.getManagedGame(gameName));
            }, BotLogger::catchRestError);
        }

        if (!isPlayerCommand) {
            return;
        }
        var player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            throw new IllegalArgumentException("Unable to determine player while attempting to run event " + event.getName() +
                    " in channel " + event.getChannel().getName() + " for game " + gameName);
        }
        this.player.set(player);
    }

    public void postExecute(SlashCommandInteractionEvent event) {
        if (saveGame) {
            GameSaveLoadManager.saveGame(game.get(), event);
        }
        game.remove();
        player.remove();
    }

    @NotNull
    public Game getGame() {
        return game.get();
    }

    @NotNull
    public Player getPlayer() {
        if (!isPlayerCommand) {
            throw new IllegalStateException("CommandGameStateHelper cannot get player state because command was not set to be a player command. This is a bug.");
        }
        return player.get();
    }
}
