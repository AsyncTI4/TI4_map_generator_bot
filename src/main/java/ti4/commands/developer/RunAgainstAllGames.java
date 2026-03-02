package ti4.commands.developer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

class RunAgainstAllGames extends Subcommand {

    private static final long ONE_SECOND_MILLIS = 1000L;

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        Set<String> changedGames = new HashSet<>();
        GamesPage.consumeAllGames(game -> {
            boolean changed = makeChanges(game, changedGames);
            if (changed) {
                changedGames.add(game.getName());
                GameManager.save(game, "Developer ran custom command against this game, probably migration related.");
            }
        });

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    private static boolean makeChanges(Game game, Collection<String> changedGames) {
        if (game.getEndedDate() != 0) return false;
        if (!game.isHasEnded()) return false;

        LocalDateTime createdDate = Instant.ofEpochMilli(game.getCreationDateTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        long fakedEndDate = createdDate.plusMonths(3).toInstant(ZoneOffset.UTC).toEpochMilli();
        game.setEndedDate(fakedEndDate);

        changedGames.add(game.getName());
        return true;
    }
}
