package ti4.helpers;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.statistics.GameStatisticFilterer;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

@UtilityClass
public class GameStatsHelper {

    public static String getWinningPath(Game game, Player winner) {
        int stage1Count = getPublicVictoryPoints(game, winner.getUserID(), 1);
        int stage2Count = getPublicVictoryPoints(game, winner.getUserID(), 2);
        int secretCount = winner.getSecretVictoryPoints();
        int supportCount = winner.getSupportForTheThroneVictoryPoints();
        String others = getOtherVictoryPoints(game, winner.getUserID());
        return stage1Count + " stage 1s, " +
            stage2Count + " stage 2s, " +
            secretCount + " secrets, " +
            supportCount + " supports" +
            (others.isEmpty() ? "" : ", " + others);
    }

    private static int getPublicVictoryPoints(Game game, String userId, int stage) {
        Map<String, List<String>> scoredPOs = game.getScoredPublicObjectives();
        int vpCount = 0;
        for (Map.Entry<String, List<String>> scoredPublic : scoredPOs.entrySet()) {
            if (scoredPublic.getValue().contains(userId)) {
                String poID = scoredPublic.getKey();
                PublicObjectiveModel po = Mapper.getPublicObjective(poID);
                if (po != null && po.getPoints() == stage) {
                    vpCount += 1;
                }
            }
        }
        return vpCount;
    }

    private static String getOtherVictoryPoints(Game game, String userId) {
        Map<String, List<String>> scoredPOs = game.getScoredPublicObjectives();
        Map<String, Integer> otherVictoryPoints = new HashMap<>();
        for (Map.Entry<String, List<String>> scoredPOEntry : scoredPOs.entrySet()) {
            if (scoredPOEntry.getValue().contains(userId)) {
                String poID = scoredPOEntry.getKey();
                PublicObjectiveModel po = Mapper.getPublicObjective(poID);
                if (po == null) {
                    int frequency = Collections.frequency(scoredPOEntry.getValue(), userId);
                    otherVictoryPoints.put(normalizeOtherVictoryPoints(poID), frequency);
                }
            }
        }
        return otherVictoryPoints.keySet().stream()
            .sorted(Comparator.reverseOrder())
            .map(key -> otherVictoryPoints.get(key) + " " + key)
            .collect(Collectors.joining(", "));
    }

    private static String normalizeOtherVictoryPoints(String otherVictoryPoint) {
        otherVictoryPoint = otherVictoryPoint.toLowerCase().replaceAll("[^a-z]", "");
        if (otherVictoryPoint.contains("seed")) {
            otherVictoryPoint = "seed";
        } else if (otherVictoryPoint.contains("mutiny")) {
            otherVictoryPoint = "mutiny";
        } else if (otherVictoryPoint.contains("shard")) {
            otherVictoryPoint = "shard";
        } else if (otherVictoryPoint.contains("custodian")) {
            otherVictoryPoint = "custodian/imperial";
        } else if (otherVictoryPoint.contains("imperial")) {
            otherVictoryPoint = "imperial rider";
        } else if (otherVictoryPoint.contains("censure")) {
            otherVictoryPoint = "censure";
        } else if (otherVictoryPoint.contains("crown") || otherVictoryPoint.contains("emph")) {
            otherVictoryPoint = "crown";
        } else {
            otherVictoryPoint = "other (probably Classified Document Leaks)";
        }
        return otherVictoryPoint;
    }

    public static void showWinsWithSupport(SlashCommandInteractionEvent event) {
        Map<Integer, Integer> supportWinCount = new HashMap<>();
        AtomicInteger gameWithWinnerCount = new AtomicInteger();
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
        for (Game game : filteredGames) {
            game.getWinner().ifPresent(winner -> {
                gameWithWinnerCount.getAndIncrement();
                int supportCount = winner.getSupportForTheThroneVictoryPoints();
                supportWinCount.put(supportCount,
                    1 + supportWinCount.getOrDefault(supportCount, 0));
            });
        }
        AtomicInteger atomicInteger = new AtomicInteger(0);
        StringBuilder sb = new StringBuilder();
        sb.append("__**Winning Paths With SftT Count:**__").append("\n");
        supportWinCount.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
            .forEach(entry -> sb.append(atomicInteger.getAndIncrement() + 1)
                .append(". `")
                .append(entry.getValue().toString())
                .append(" (")
                .append(Math.round(100 * entry.getValue() / (double) gameWithWinnerCount.get()))
                .append("%)` ")
                .append(entry.getKey())
                .append(" SftT wins")
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "SftT wins", sb.toString());
    }

    public static Map<String, Integer> getAllWinningPathCounts(List<Game> games) {
        Map<String, Integer> winningPathCount = new HashMap<>();
        for (Game game : games) {
            game.getWinner().ifPresent(winner -> {
                String path = GameStatsHelper.getWinningPath(game, winner);
                winningPathCount.put(path,
                    1 + winningPathCount.getOrDefault(path, 0));
            });
        }
        return winningPathCount;
    }
}
