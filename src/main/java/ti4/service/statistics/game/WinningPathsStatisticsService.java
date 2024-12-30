package ti4.service.statistics.game;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.message.MessageHelper;

import static ti4.helpers.StringHelper.ordinal;

@UtilityClass
class WinningPathsStatisticsService {

    static void showWinningPaths(SlashCommandInteractionEvent event) {
        Map<String, Integer> winningPathCount = new HashMap<>();

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getGamesFilter(event),
            game -> getWinningPath(game, winningPathCount)
        );

        int gamesWithWinnerCount = winningPathCount.values().stream().reduce(0, Integer::sum);
        AtomicInteger atomicInteger = new AtomicInteger();
        StringBuilder sb = new StringBuilder();
        sb.append("__**Winning Paths Count:**__").append("\n");
        winningPathCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> sb.append(atomicInteger.incrementAndGet())
                .append(". `")
                .append(entry.getValue().toString())
                .append(" (")
                .append(Math.round(100 * entry.getValue() / (double) gamesWithWinnerCount))
                .append("%)` ")
                .append(entry.getKey())
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Winning Paths", sb.toString());
    }

    private static void getWinningPath(Game game, Map<String, Integer> winningPathCount) {
        game.getWinner().ifPresent(winner -> {
            String path = WinningPathHelper.buildWinningPath(game, winner);
            winningPathCount.put(path, 1 + winningPathCount.getOrDefault(path, 0));
        });
    }

    static void showWinsWithSupport(SlashCommandInteractionEvent event) {
        Map<Integer, Integer> supportWinCount = new HashMap<>();
        AtomicInteger gameWithWinnerCount = new AtomicInteger();

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getGamesFilter(event),
            game -> getWinsWithSupport(game, supportWinCount, gameWithWinnerCount)
        );

        AtomicInteger atomicInteger = new AtomicInteger();
        StringBuilder sb = new StringBuilder();
        sb.append("__**Winning Paths Holding _Support for the Throne_ Count:**__").append("\n");
        supportWinCount.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
            .forEach(entry -> sb.append(atomicInteger.getAndIncrement() + 1)
                .append(". `")
                .append(entry.getValue().toString())
                .append(" (")
                .append(Math.round(100 * entry.getValue() / (double) gameWithWinnerCount.get()))
                .append("%)` ")
                .append(entry.getKey())
                .append(" _Support for the Throne_ wins")
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Support for the Throne wins", sb.toString());
    }

    private static void getWinsWithSupport(Game game, Map<Integer, Integer> supportWinCount, AtomicInteger gameWithWinnerCount) {
        game.getWinner().ifPresent(winner -> {
            gameWithWinnerCount.getAndIncrement();
            int supportCount = winner.getSupportForTheThroneVictoryPoints();
            supportWinCount.put(supportCount, 1 + supportWinCount.getOrDefault(supportCount, 0));
        });
    }

    static String compareWinningPathToAllOthers(String winningPath, int playerCount, int victoryPointTotal) {
        StringBuilder sb = new StringBuilder();
        Map<String, Integer> winningPathCounts = getNormalGameWinningPaths(playerCount, victoryPointTotal);
        int gamesWithWinnerCount = winningPathCounts.values().stream().reduce(0, Integer::sum);
        if (gamesWithWinnerCount >= 100) {
            // TODO: Previously this was never null, but after loadless it is? Need investigation, but for now defaulting to 1.
            int winningPathCount = winningPathCounts.getOrDefault(winningPath, 1);
            double winningPathPercent = winningPathCount / (double) gamesWithWinnerCount;
            String winningPathCommonality = getWinningPathCommonality(winningPathCounts, winningPathCount);
            sb.append("Out of ").append(gamesWithWinnerCount).append(" similar games (").append(victoryPointTotal).append(" victory points, ")
                .append(playerCount).append(" player)")
                .append(", this path has been seen ")
                .append(winningPathCount - 1)
                .append(" times before. It's the ").append(winningPathCommonality).append(" most common path (out of ")
                .append(winningPathCounts.size()).append(" paths) at ")
                .append(formatPercent(winningPathPercent)).append(" of games.").append("\n");
            if (winningPathCount == 1) {
                sb.append("🥳__**An async first! May your victory live on for all to see!**__🥳").append("\n");
            } else if (winningPathPercent <= .005) {
                sb.append("🎉__**Few have traveled your path! We celebrate your boldness!**__🎉").append("\n");
            } else if (winningPathPercent <= .01) {
                sb.append("🎉__**Who needs a conventional win? Not you!**__🎉").append("\n");
            }
        }
        return sb.toString();
    }

    private static Map<String, Integer> getNormalGameWinningPaths(int playerCount, int victoryPointTotal) {
        Map<String, Integer> winningPathCount = new HashMap<>();

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getNormalFinishedGamesFilter(playerCount, victoryPointTotal),
            game -> getWinningPath(game, winningPathCount)
        );

        return winningPathCount;
    }

    private static String getWinningPathCommonality(Map<String, Integer> winningPathCounts, int winningPathCount) {
        int commonality = 1;
        for (int i : winningPathCounts.values()) {
            if (i > winningPathCount) {
                commonality++;
            }
        }
        return commonality == 1 ? "" : ordinal(commonality);
    }

    private static String formatPercent(double d) {
        NumberFormat numberFormat = NumberFormat.getPercentInstance();
        numberFormat.setMinimumFractionDigits(1);
        return numberFormat.format(d);
    }
}
