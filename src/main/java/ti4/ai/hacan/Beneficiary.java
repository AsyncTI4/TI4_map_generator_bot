package ti4.ai.hacan;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class Beneficiary {

    public static Optional<Player> leastThreatening(Game game, Player seat, Collection<Player> candidates) {
        List<Player> others = candidates.stream()
                .filter(candidate -> candidate != null && candidate != seat)
                .distinct()
                .toList();
        if (others.isEmpty()) return Optional.empty();
        List<Player> safe = others.stream()
                .filter(candidate -> !isPointsLeader(game, seat, candidate))
                .toList();
        List<Player> pool = safe.isEmpty() ? others : safe;
        return pool.stream()
                .min(Comparator.comparingDouble((Player candidate) -> threat(game, candidate))
                        .thenComparingInt(candidate -> candidate.getTg() + candidate.getCommodities()));
    }

    public static boolean isPointsLeader(Game game, Player seat, Player candidate) {
        int points = candidate.getTotalVictoryPoints();
        if (points <= 0) return false;
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .allMatch(other -> other.getTotalVictoryPoints() <= points);
    }

    static double threat(Game game, Player player) {
        int ready = ObjectivePolicy.scorablePublics(game, player).stream()
                .mapToInt(ObjectivePolicy::victoryPoints)
                .max()
                .orElse(0);
        return player.getTotalVictoryPoints() + ready;
    }
}
