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
        Game eventGame = GameManager.getGame(gameName);
        eventGame.incrementSpecificSlashCommandCount(event.getFullCommandName());
        game.set(eventGame);

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
        var gamePlayer = CommandHelper.getPlayerFromEvent(eventGame, event);
        if (gamePlayer == null) {
            throw new IllegalArgumentException("Unable to determine player while attempting to run event " + event.getName() +
                    " in channel " + event.getChannel().getName() + " for game " + gameName);
        }
        player.set(gamePlayer);
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
