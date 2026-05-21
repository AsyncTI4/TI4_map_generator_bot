package ti4.discord.interactions.commands.developer;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

class RunAgainstAllGames extends Subcommand {

    private static final long ONE_DAY_MILLIS = Duration.ofDays(1).toMillis();
    private static final long THIRTY_DAYS_MILLIS = Duration.ofDays(30).toMillis();

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        Set<String> changedGames = new HashSet<>();
        ConsumeGameUtility.consumeAllGames(
                game -> {
                    boolean changed = makeChanges(game);
                    if (changed) {
                        changedGames.add(game.getName());
                        GameManager.save(
                                game, "Developer ran custom command against this game, probably migration related.");
                    }
                },
                ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    private static boolean makeChanges(Game game) {
        // Migration: move slashCommandsUsed and actionCardsSabotaged from old text-based
        // persistence format (SLASH_COMMAND_STRING / ACS_SABOD lines) to the GameStats JSON object.
        // These are already loaded into GameStats during deserialization of old game files,
        // so returning true for any game that has stats data forces a re-save in the new format.
        return !game.getGameStats().getSlashCommandsUsed().isEmpty()
                || !game.getGameStats().getActionCardsSabotaged().isEmpty();
    }
}
