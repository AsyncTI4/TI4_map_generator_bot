package ti4.service.statistics.game;

import java.util.concurrent.atomic.AtomicInteger;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
class SpendToWinCorrelationStatisticsService {

    static void calculateSpendToWinCorrelation(SlashCommandInteractionEvent event) {
        StringBuilder names = new StringBuilder();
        AtomicInteger num = new AtomicInteger();
        AtomicInteger gamesWhereHighestWon = new AtomicInteger();

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getGamesFilter(event),
            game -> calculate(game, num, gamesWhereHighestWon, names)
        );

        names.append("Total games where highest spender won was ").append(gamesWhereHighestWon).append(" out of ").append(num);
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Game Expenses", names.toString());
    }

    private static void calculate(Game game, AtomicInteger num, AtomicInteger gamesWhereHighestWon, StringBuilder names) {
        if (game.getWinner().isEmpty()) {
            return;
        }

        int highest = 0;
        Player winner = game.getWinner().get();
        Player highestP = null;
        for (Player player : game.getRealAndEliminatedAndDummyPlayers()) {
            if (player.getTotalExpenses() > highest) {
                highestP = player;
                highest = player.getTotalExpenses();
            }
            if (player.getTotalExpenses() < 20) {
                highestP = null;
                break;
            }
        }
        if (highestP != null) {
            num.incrementAndGet();
            names.append(num).append(". ").append(game.getName());
            names.append(" - Winner was ").append(winner.getFactionEmoji()).append(" (").append("Highest was ").append(highestP.getFactionEmoji()).append(" at ").append(highestP.getTotalExpenses()).append(")");
            names.append("\n");
            if (highestP == winner) {
                gamesWhereHighestWon.incrementAndGet();
            }
        }
    }
}
