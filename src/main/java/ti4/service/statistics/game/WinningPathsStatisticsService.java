package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
class WinningPathsStatisticsService {

    static void showWinningPaths(SlashCommandInteractionEvent event) {
        Map<String, Integer> winningPathCount = new HashMap<>();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event), game -> getWinningPath(game, winningPathCount));

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
        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Winning Paths", sb.toString());
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
                GameStatisticsFilterer.getGamesFilterForWonGame(event),
                game -> getWinsWithSupport(game, supportWinCount, gameWithWinnerCount));

        AtomicInteger atomicInteger = new AtomicInteger();
        StringBuilder sb = new StringBuilder();
        sb.append("__**Winning Paths Holding _Support for the Throne_ Count:**__")
                .append("\n");
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
        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Support for the Throne wins", sb.toString());
    }

    private static void getWinsWithSupport(
            Game game, Map<Integer, Integer> supportWinCount, AtomicInteger gameWithWinnerCount) {
        game.getWinner().ifPresent(winner -> {
            gameWithWinnerCount.getAndIncrement();
            int supportCount = winner.getSupportForTheThroneVictoryPoints();
            supportWinCount.put(supportCount, 1 + supportWinCount.getOrDefault(supportCount, 0));
        });
    }
}
