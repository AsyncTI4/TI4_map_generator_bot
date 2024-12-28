package ti4.service.statistics.game;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;

@UtilityClass
public class WinningPathHelper {

    public static String buildWinningPath(Game game, Player winner) {
        int stage1Count = countPublicVictoryPoints(game, winner.getUserID(), 1);
        int stage2Count = countPublicVictoryPoints(game, winner.getUserID(), 2);
        int secretCount = winner.getSecretVictoryPoints();
        int supportCount = winner.getSupportForTheThroneVictoryPoints();
        String otherPoints = summarizeOtherVictoryPoints(game, winner.getUserID());
        
        if (supportCount >= 2)
        {
            return String.format(
                "%d stage 1 objectives, %d stage 2 objectives, %d secret objectives, %d Supports for the Thrones%s",
                stage1Count, stage2Count, secretCount, supportCount,
                otherPoints.isEmpty() ? "" : ", " + otherPoints
            );
        }

        return String.format(
            "%d stage 1 objectives, %d stage 2 objectives, %d secret objectives, %d Support for the Throne%s",
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
                WinningPathHelper::normalizeVictoryPointKey,
                key -> Collections.frequency(game.getScoredPublicObjectives().get(key), userId),
                Integer::sum
            ));

        return otherVictoryPoints.entrySet().stream()
            .sorted(
                Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())
            )
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
