package ti4.commands.developer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
        addOptions(GameStatisticsFilterer.gameStatsFilters());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        List<String> changedGames = new ArrayList<>();
        GamesPage.consumeAllGames(game -> {
            boolean changed = makeChanges(game);
            if (changed) {
                changedGames.add(game.getName());
                GameManager.save(game, "Developer ran custom command against this game, probably migration related.");
            }
        });

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    private static boolean makeChanges(Game game) {
        int gameNameHash = game.getName().hashCode();
        int hours = Math.floorMod(gameNameHash, 24);
        int minutes = Math.floorMod(gameNameHash, 60);

        int customNameHash = game.getCustomName().hashCode();
        int seconds = Math.floorMod(customNameHash, 60);
        int nanoseconds = Math.floorMod(gameNameHash, 1_000_000_000);

        LocalDate creationDate = GameHelper.getCreationDateAsLocalDate(game);
        LocalDateTime creationDateWithFakeTime = creationDate.atTime(hours, minutes, seconds, nanoseconds);
        game.setCreationDateTime(
                creationDateWithFakeTime.toInstant(ZoneOffset.UTC).toEpochMilli());
        return true;
    }
}
