package ti4.spring.service.statistics.matchmaking;

import de.gesundkrank.jskills.GameInfo;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MatchmakingGameInfo {

    // jskills default.
    private static final double INITIAL_MEAN = 25.0;
    // jskills default (mean / 3).
    private static final double INITIAL_STANDARD_DEVIATION = INITIAL_MEAN / 3.0;
    // jskills default (mean / 6).
    private static final double BETA = INITIAL_MEAN / 6.0;
    // jskills default.
    private static final double DRAW_PROBABILITY = 0.10;
    // jskills default (sigma / 100).
    private static final double ASSUMED_SKILL_DRIFT_BETWEEN_GAMES = INITIAL_STANDARD_DEVIATION / 100.0;

    public static GameInfo create() {
        return new GameInfo(
                INITIAL_MEAN, INITIAL_STANDARD_DEVIATION, BETA, ASSUMED_SKILL_DRIFT_BETWEEN_GAMES, DRAW_PROBABILITY);
    }
}
