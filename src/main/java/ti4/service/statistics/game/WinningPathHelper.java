package ti4.service.statistics.game;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;

@UtilityClass
public class WinningPathHelper {

    private static final Pattern PATTERN = Pattern.compile("[^a-z]");

    public static String buildWinningPath(Game game, Player winner) {
        return describe(breakDownWinningPath(game, winner));
    }

    public static WinningPathBreakdown breakDownWinningPath(Game game, Player winner) {
        return new WinningPathBreakdown(
                countPublicVictoryPoints(game, winner.getUserID(), 1),
                countPublicVictoryPoints(game, winner.getUserID(), 2),
                winner.getSecretVictoryPoints(),
                winner.getSupportForTheThroneVictoryPoints(),
                otherVictoryPoints(game, winner.getUserID()));
    }

    private static String describe(WinningPathBreakdown path) {
        String otherPoints = summarizeOtherVictoryPoints(path.otherPoints());
        return String.format(
                "%d stage 1 objectives, %d stage 2 objectives, %d secret objectives, %d %s%s",
                path.stage1s(),
                path.stage2s(),
                path.secrets(),
                path.supports(),
                path.supports() >= 2 ? "_Supports for the Thrones_" : "_Support for the Throne_",
                otherPoints.isEmpty() ? "" : ", " + otherPoints);
    }

    private static int countPublicVictoryPoints(Game game, String userId, int stage) {
        return (int) game.getScoredPublicObjectives().entrySet().stream()
                .filter(entry -> entry.getValue().contains(userId))
                .map(Map.Entry::getKey)
                .map(Mapper::getPublicObjective)
                .filter(po -> po != null && po.getPoints() == stage)
                .count();
    }

    private static Map<String, Integer> otherVictoryPoints(Game game, String userId) {
        return game.getScoredPublicObjectives().entrySet().stream()
                .filter(entry -> entry.getValue().contains(userId))
                .map(Map.Entry::getKey)
                .filter(poID -> Mapper.getPublicObjective(poID) == null)
                .filter(poID -> !isZeroVictoryPointObjective(game, poID))
                .collect(Collectors.toMap(
                        WinningPathHelper::normalizeVictoryPointKey,
                        key -> Collections.frequency(
                                game.getScoredPublicObjectives().get(key), userId),
                        Integer::sum));
    }

    private static String summarizeOtherVictoryPoints(Map<String, Integer> otherVictoryPoints) {
        return otherVictoryPoints.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue()
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> entry.getValue() + " " + entry.getKey())
                .collect(Collectors.joining(", "));
    }

    private static boolean isZeroVictoryPointObjective(Game game, String poID) {
        Integer vp = game.getCustomPublicVP().get(poID);
        return vp != null && vp == 0;
    }

    private static String normalizeVictoryPointKey(String poID) {
        String normalized = PATTERN.matcher(poID.toLowerCase()).replaceAll("");
        if (normalized.contains("seed")) return WinningPathBreakdown.SEED;
        if (normalized.contains("mutiny")) return WinningPathBreakdown.MUTINY;
        if (normalized.contains("shard")) return WinningPathBreakdown.SHARD;
        if (normalized.contains("custodian")) return WinningPathBreakdown.CUSTODIAN_OR_IMPERIAL;
        if (normalized.contains("imperial")) return WinningPathBreakdown.IMPERIAL_RIDER;
        if (normalized.contains("censure")) return WinningPathBreakdown.CENSURE;
        if (normalized.contains("crown") || normalized.contains("emph")) return WinningPathBreakdown.CROWN;
        if (normalized.contains("latvinia")) return WinningPathBreakdown.LATVINIA;
        if (normalized.contains("song")) return WinningPathBreakdown.STYX;
        return WinningPathBreakdown.UNRECOGNIZED;
    }
}
