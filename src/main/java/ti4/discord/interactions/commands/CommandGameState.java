package ti4.discord.interactions.commands;

import java.util.HashMap;
import java.util.Map;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.logging.RollbarManager;
import ti4.service.event.EventAuditService;
import ti4.service.game.GameNameService;
import ti4.spring.service.gameevent.GameEventDraft;
import ti4.spring.service.gameevent.GameEventService;
import ti4.spring.service.gameevent.GameEventType;
import ti4.spring.service.gameevent.GameSubEvent;

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
            // Emit the manual-command event BEFORE the save so its counter increment is persisted with the game.
            logManualCommand(event, game);
            GameManager.save(game, EventAuditService.getReason(event));
        }
        clear();
    }

    private static void logManualCommand(SlashCommandInteractionEvent event, Game game) {
        if (game == null) return;

        String commandString = event.getCommandString();
        Member member = event.getMember();
        String userName = member == null ? null : member.getEffectiveName();

        // Nest under an open tactical-action draft when present; otherwise emit a top-level event.
        if (GameEventDraft.stage(game, new GameSubEvent.ManualCommand(userName, commandString))) {
            return;
        }

        // Attribute to the invoking user's player (not any target-player option) when resolvable.
        Player player =
                CommandHelper.getPlayerFromGame(game, member, event.getUser().getId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("command", commandString);
        if (userName != null) {
            payload.put("user", userName);
        }
        GameEventService.commit(game, GameEventType.MANUAL_COMMAND, player, payload);
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
