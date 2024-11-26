package ti4.service.statistics.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.statistics.GameStatisticsFilterer;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
class WinningPathsStatisticsService {

    static void showWinningPath(SlashCommandInteractionEvent event) {
        Map<String, Integer> winningPathCount = new HashMap<>();

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getGamesFilter(event),
            game -> getWinningPaths(game, winningPathCount)
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

    private static void getWinningPaths(Game game, Map<String, Integer> winningPathCount) {
        game.getWinner().ifPresent(winner -> {
            String path = buildWinningPath(game, winner);
            winningPathCount.put(path, 1 + winningPathCount.getOrDefault(path, 0));
        });
    }

    private static String buildWinningPath(Game game, Player winner) {
        int stage1Count = countPublicVictoryPoints(game, winner.getUserID(), 1);
        int stage2Count = countPublicVictoryPoints(game, winner.getUserID(), 2);
        int secretCount = winner.getSecretVictoryPoints();
        int supportCount = winner.getSupportForTheThroneVictoryPoints();
        String otherPoints = summarizeOtherVictoryPoints(game, winner.getUserID());

        return String.format(
            "%d stage 1s, %d stage 2s, %d secrets, %d supports%s",
            stage1Count, stage2Count, secretCount, supportCount,
            otherPoints.isEmpty() ? "" : ", " + otherPoints
        );
    }

    private static int countPublicVictoryPoints(Game game, String userId, int stage) {
        return (int) game.getScoredPublicObjectives().entrySet().stream()
            .filter(entry -> entry.getValue().contains(userId))
            .map(Map.Entry::getKey)
            .map(Mapper::getPublicObjective)
            .filter(po -> po != null && po.getPoints() == stage)
            .count();
    }

    private static String summarizeOtherVictoryPoints(Game game, String userId) {
        Map<String, Integer> otherVictoryPoints = game.getScoredPublicObjectives().entrySet().stream()
            .filter(entry -> entry.getValue().contains(userId))
            .map(Map.Entry::getKey)
            .filter(poID -> Mapper.getPublicObjective(poID) == null)
            .collect(Collectors.toMap(
                WinningPathsStatisticsService::normalizeVictoryPointKey,
                key -> Collections.frequency(game.getScoredPublicObjectives().get(key), userId),
                Integer::sum
            ));

        return otherVictoryPoints.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
            .map(entry -> entry.getValue() + " " + entry.getKey())
            .collect(Collectors.joining(", "));
    }

    private static String normalizeVictoryPointKey(String poID) {
        String normalized = poID.toLowerCase().replaceAll("[^a-z]", "");
        if (normalized.contains("seed")) return "seed";
        if (normalized.contains("mutiny")) return "mutiny";
        if (normalized.contains("shard")) return "shard";
        if (normalized.contains("custodian")) return "custodian/imperial";
        if (normalized.contains("imperial")) return "imperial rider";
        if (normalized.contains("censure")) return "censure";
        if (normalized.contains("crown") || normalized.contains("emph")) return "crown";
        return "other (probably Classified Document Leaks)";
    }
}
