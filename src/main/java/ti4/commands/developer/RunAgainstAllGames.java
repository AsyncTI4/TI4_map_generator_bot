package ti4.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;

class RunAgainstAllGames extends Subcommand {

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
                GameManager.save(game, "Developer ran custom command against this game, probably migration related.");
            }
        });

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
    }

    private static boolean makeChanges(Game game) {
        return false;
    }
}
