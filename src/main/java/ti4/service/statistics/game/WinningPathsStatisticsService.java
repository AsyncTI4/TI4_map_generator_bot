package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.message.MessageHelper;

@UtilityClass
class WinningPathsStatisticsService {

    static void showWinningPaths(SlashCommandInteractionEvent event) {
        Map<String, Integer> winningPathCount = new HashMap<>();

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event),
                game -> getWinningPath(game, winningPathCount),
                ExecutionLockType.READ);

        int gamesWithWinnerCount = winningPathCount.values().stream().reduce(0, Integer::sum);
        AtomicInteger atomicInteger = new AtomicInteger();
        StringBuilder sb = new StringBuilder();
        sb.append("__**Winning Paths Count:**__").append('\n');
        winningPathCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> sb.append(atomicInteger.incrementAndGet())
                        .append(". `")
                        .append(entry.getValue())
                        .append(" (")
                        .append(Math.round(100 * entry.getValue() / (double) gamesWithWinnerCount))
                        .append("%)` ")
                        .append(entry.getKey())
                        .append('\n'));
        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Winning Paths", sb.toString());
    }

    private static void getWinningPath(Game game, Map<String, Integer> winningPathCount) {
        game.getWinner().ifPresent(winner -> {
            String path = WinningPathHelper.buildWinningPath(game, winner);
            winningPathCount.put(path, 1 + winningPathCount.getOrDefault(path, 0));
        });
    }
}
