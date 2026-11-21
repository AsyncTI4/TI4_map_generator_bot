package ti4.ai.endurance;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class Endurance {

    public static final String SECRET = "pe";
    static final String SLING_RELAY = "sr";
    static final String BIO_STIMS = "bs";
    private static final double SPREAD = 1.5;
    private static final double MIN_CHANCE = 0.3;
    private static final double LEADERSHIP_TOKEN_VALUE = 0.5;
    private static final double STALL_TECH_VALUE = 0.75;

    public record Race(double mine, double rival, double chance) {

        public boolean contested() {
            return chance >= MIN_CHANCE;
        }
    }

    public static boolean holds(Player seat) {
        return seat.getSecretsUnscored().containsKey(SECRET);
    }

    public static Optional<Race> race(Game game, Player seat) {
        if (!holds(seat) || !"action".equalsIgnoreCase(game.getPhaseOfGame())) return Optional.empty();
        List<Player> rivals = rivals(game, seat);
        if (rivals.isEmpty()) return Optional.empty();
        double mine = StallCount.of(game, seat, seat);
        double rival = rivals.stream()
                .mapToDouble(other -> StallCount.of(game, other, seat))
                .max()
                .orElse(0);
        return Optional.of(new Race(mine, rival, chance(mine - rival)));
    }

    public static double stallBonus(Game game, Player seat) {
        return race(game, seat)
                .filter(Race::contested)
                .map(race -> race.chance() * stake(game, seat) / (race.rival() + 1))
                .orElse(0.0);
    }

    public static boolean savesCardsForLater(Game game, Player seat) {
        return race(game, seat).filter(race -> !race.contested()).isPresent();
    }

    public static double leadershipValue(Player seat, int tokens) {
        return holds(seat) ? LEADERSHIP_TOKEN_VALUE * tokens : 0;
    }

    public static double researchValue(Player seat, String alias) {
        boolean stalls = SLING_RELAY.equals(alias) || BIO_STIMS.equals(alias);
        return stalls && holds(seat) ? STALL_TECH_VALUE : 0;
    }

    static double chance(double lead) {
        return 1 / (1 + Math.exp(-lead / SPREAD));
    }

    private static double stake(Game game, Player seat) {
        return ScoringStakes.scoreValue(game, seat, 1);
    }

    private static List<Player> rivals(Game game, Player seat) {
        return game.getRealPlayers().stream()
                .filter(other -> other != seat && !other.isPassed() && !other.isEliminated())
                .toList();
    }
}
