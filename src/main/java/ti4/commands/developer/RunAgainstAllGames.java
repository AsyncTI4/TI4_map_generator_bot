package ti4.commands.developer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

class RunAgainstAllGames extends Subcommand {

    private static final int DAYS_AFTER_CREATION_TO_DEFAULT_END = 60;

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
        addOptions(GameStatisticsFilterer.gameStatsFilters());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        GamesPage.consumeAllGames(GameStatisticsFilterer.getGamesFilter(event), game -> {
            boolean changed = update1(game);
            boolean changed2 = update2(game);
            if (changed || changed2) {
                GameManager.save(game, "Developer ran custom command against this game, probably migration related.");
            }
        });

        BotLogger.info("Finished custom command against all games.");
    }

    private static boolean update1(Game game) {
        if (game.hasWinner() && !game.isHasEnded()) {
            game.setHasEnded(true);
            BotLogger.info(String.format("Ended game %s", game.getName()));
            return true;
        }
        return false;
    }

    private static boolean update2(Game game) {
        if (game.isHasEnded() && game.getEndedDate() == 0) {
            DateTimeFormatter FMT = DateTimeFormatter.ofPattern("uuuu.MM.dd", Locale.ROOT);
            LocalDate creationDate = LocalDate.parse(game.getCreationDate(), FMT);
            long endDateMilliseconds = creationDate
                    .plusDays(DAYS_AFTER_CREATION_TO_DEFAULT_END)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli();
            game.setEndedDate(endDateMilliseconds);
            BotLogger.info(String.format("Set game %s ended date to %d", game.getName(), game.getEndedDate()));
            return true;
        }
        return false;
    }
}
