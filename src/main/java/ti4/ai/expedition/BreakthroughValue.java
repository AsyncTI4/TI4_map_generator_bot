package ti4.ai.expedition;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.GameHorizon;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.map.FractureService;

@UtilityClass
public class BreakthroughValue {

    public static final double BASE = 2.0;
    public static final double PER_WIN_POINT = 0.5;
    public static final double LEAST = 0.75;
    public static final double FRACTURE_CHANCE = 0.2;
    public static final double FRACTURE_VALUE = 2.0;
    static final double FULL_VALUE_ROUNDS = 4;
    public static final double PRIORITY_FLOOR = 8.0;
    private static final Map<String, Double> PRIORITY_FLOORS =
            Map.of("hacan", PRIORITY_FLOOR, "letnev", PRIORITY_FLOOR);

    public static boolean isLocked(Player seat) {
        return !seat.getBreakthroughIDs().isEmpty()
                && seat.getBreakthroughIDs().stream().anyMatch(id -> !seat.isBreakthroughUnlocked(id));
    }

    public static boolean isPriority(Player seat) {
        return isLocked(seat) && PRIORITY_FLOORS.containsKey(seat.getFaction());
    }

    public static double of(Game game, Player seat) {
        if (!isLocked(seat)) return 0;
        double lift = ExpeditionStats.breakthroughLiftPoints(seat.getFaction());
        double floor = PRIORITY_FLOORS.getOrDefault(seat.getFaction(), 0.0);
        return Math.max(floor, Math.max(LEAST, BASE + PER_WIN_POINT * lift)) * timing(game);
    }

    public static double fractureRoll(Game game, Player seat) {
        if (!isLocked(seat) || !FractureService.canFractureEnterPlay(game)) return 0;
        return FRACTURE_CHANCE * FRACTURE_VALUE * timing(game);
    }

    private static double timing(Game game) {
        return Math.min(1.0, GameHorizon.roundsLeft(game) / FULL_VALUE_ROUNDS);
    }
}
