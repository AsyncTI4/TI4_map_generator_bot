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
                    boolean changed = makeChanges(game, event);
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

    private static boolean makeChanges(Game game, SlashCommandInteractionEvent event) {
        if (!game.isHasEnded()) {
            return false;
        }

        long now = System.currentTimeMillis();
        long creationDateTime = game.getCreationDateTime();
        long endedDate = game.getEndedDate();
        boolean changed = false;

        if (creationDateTime > now) {
            long newCreationDateTime = now - ONE_DAY_MILLIS;
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    game.getName() + " has creationDateTime in the future. creationDateTime=" + creationDateTime
                            + ". Setting to " + newCreationDateTime + ".");
            game.setCreationDateTime(newCreationDateTime);
            creationDateTime = newCreationDateTime;
            changed = true;
        }

        if (endedDate < creationDateTime) {
            long daysToEnd = Duration.ofMillis(endedDate - creationDateTime).toDays();
            long newEndedDate = Math.min(creationDateTime + THIRTY_DAYS_MILLIS, now);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    game.getName() + " ended before it started (" + daysToEnd + " days). creationDateTime="
                            + creationDateTime + ", endedDate=" + endedDate
                            + ". Updating end date to " + newEndedDate + ".");
            game.setEndedDate(newEndedDate);
            changed = true;
        }

        return changed;
    }
}
