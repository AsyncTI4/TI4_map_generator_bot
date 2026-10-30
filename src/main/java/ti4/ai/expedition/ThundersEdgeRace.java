package ti4.ai.expedition;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.GameHorizon;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ThundersEdgeRace {

    public static final double HOLD_VALUE = ObjectiveValue.VICTORY_POINT_VALUE;
    static final double RIVAL_GRANT_SHARE = 0.5;
    static final double LEAD_STEP = 0.6;
    static final double LOCKED_CHANCE = 0.95;
    static final double LEAST_CHANCE = 0.03;
    static final double MOST_CHANCE = 0.9;
    static final double FULL_VALUE_ROUNDS = 4;

    public static double gainFromClaiming(Game game, Player seat) {
        Map<String, Integer> counts = counts(game);
        int remaining = game.getExpeditions().getRemainingExpeditionCount();
        if (remaining <= 0) return 0;
        int mine = counts.getOrDefault(seat.getFaction(), 0);
        int rivals = counts.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(seat.getFaction()))
                .mapToInt(Map.Entry::getValue)
                .max()
                .orElse(0);
        int players = Math.max(1, game.getRealPlayers().size());
        double before = holdChance(mine, rivals, remaining, players, false);
        double after = holdChance(mine + 1, rivals, remaining - 1, players, remaining == 1);
        double lost = remaining == 1 && after == 0 ? RIVAL_GRANT_SHARE : 0;
        return HOLD_VALUE * timing(game) * (after - before - lost);
    }

    static double holdChance(int mine, int rivals, int remaining, int players, boolean claimedLast) {
        if (remaining == 0) return mine > rivals || (mine == rivals && claimedLast) ? 1 : 0;
        if (mine > rivals + remaining) return LOCKED_CHANCE;
        double step = LEAD_STEP / (remaining + 2);
        return Math.clamp(1.0 / players + step * (mine - rivals), LEAST_CHANCE, MOST_CHANCE);
    }

    static Map<String, Integer> counts(Game game) {
        Map<String, Integer> counts = new HashMap<>();
        Collection<String> claimers =
                game.getExpeditions().getExpeditionFactions().values();
        claimers.stream().filter(faction -> faction != null).forEach(faction -> counts.merge(faction, 1, Integer::sum));
        return counts;
    }

    private static double timing(Game game) {
        return Math.min(1.0, GameHorizon.roundsLeft(game) / FULL_VALUE_ROUNDS);
    }
}
