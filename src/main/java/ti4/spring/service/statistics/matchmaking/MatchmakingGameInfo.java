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
    // Default is .1. However, our draw probably is truly .5, due to treating 7-9 points as equal.
    private static final double DRAW_PROBABILITY = 0.25;
    // Default divides by 100. However, we divide by 50 to increase the number of points players
    // lose and gain each game.
    private static final double ASSUMED_SKILL_DRIFT_BETWEEN_GAMES = INITIAL_STANDARD_DEVIATION / 25.0;

    public static GameInfo create() {
        return new GameInfo(
                INITIAL_MEAN, INITIAL_STANDARD_DEVIATION, BETA, ASSUMED_SKILL_DRIFT_BETWEEN_GAMES, DRAW_PROBABILITY);
    }
}
