package ti4.commands.developer;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.helper.GameHelper;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

class RunAgainstAllGames extends Subcommand {

    private static final long CUTOFF_DATE =
            LocalDate.of(2024, 10, 31).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
        addOptions(GameStatisticsFilterer.gameStatsFilters());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        GamesPage.consumeAllGames(GameStatisticsFilterer.getGamesFilter(event), game -> {
            boolean changed = makeChanges(game);
            if (changed) {
                BotLogger.info("Changes made to " + game.getName() + ".");
                GameManager.save(game, "Developer ran custom command against this game, probably migration related.");
            }
        });

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
    }

    private static boolean makeChanges(Game game) {
        long startedDate = getStartedDate(game);
        if (startedDate >= CUTOFF_DATE) {
            return false;
        }

        if (!hasCompletedExpedition(game)) {
            return false;
        }

        if (game.getTags().contains("ThundersEdgeDemo")) {
            return false;
        }

        return game.addTag("ThundersEdgeDemo");
    }

    private static long getStartedDate(Game game) {
        long startedDate = game.getStartedDate();
        if (startedDate > 0) {
            return startedDate;
        }

        try {
            return GameHelper.getCreationDateAsEpochMillis(game);
        } catch (Exception e) {
            BotLogger.info("Could not handle creation date for " + game.getName() + ": " + e.getMessage());
            return CUTOFF_DATE;
        }
    }

    private static boolean hasCompletedExpedition(Game game) {
        return game.getExpeditions().getExpeditionFactions().values().stream().anyMatch(Objects::nonNull);
    }
}
