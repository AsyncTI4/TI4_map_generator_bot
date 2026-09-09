package ti4.spring.service.statistics.matchmaking;

import de.gesundkrank.jskills.GameInfo;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MatchmakingGameInfo {

    // Default.
    private static final double INITIAL_MEAN = 25.0;
    // Default.
    private static final double INITIAL_STANDARD_DEVIATION = INITIAL_MEAN / 3.0;
    // Default.
    private static final double BETA = INITIAL_MEAN / 6.0;
    // Default is .1. Our observed tie rate is about .5, because players within 3 points of the goal
    // all share a rank. We deliberately use a lower value: it tightens the rating spread and smooths
    // the top of the ladder, and measured prediction accuracy is unchanged from .05 through .5.
    private static final double DRAW_PROBABILITY = 0.05;
    // Default divides by 100. However, we divide by 50 to increase the number of points players
    // lose and gain each game.
    private static final double ASSUMED_SKILL_DRIFT_BETWEEN_GAMES = INITIAL_STANDARD_DEVIATION / 50.0;

    public static GameInfo create() {
        return new GameInfo(
                INITIAL_MEAN, INITIAL_STANDARD_DEVIATION, BETA, ASSUMED_SKILL_DRIFT_BETWEEN_GAMES, DRAW_PROBABILITY);
    }
}
