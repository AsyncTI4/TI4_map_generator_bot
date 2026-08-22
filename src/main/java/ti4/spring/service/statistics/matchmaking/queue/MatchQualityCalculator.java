package ti4.spring.service.statistics.matchmaking.queue;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.ITeam;
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Rating;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.trueskill.FactorGraphTrueSkillCalculator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * TrueSkill's raw match quality (draw probability) penalizes rating uncertainty: a perfectly balanced
 * table of new players (high sigma) scores a few percent while the same table of calibrated players
 * scores far higher. To judge balance independently of uncertainty, we also compute the ideal quality
 * for the same players with all means equalized (keeping their real sigmas) — the mean-imbalance term
 * becomes 1 while the mean-independent uncertainty term is unchanged. The ratio of actual to ideal is a
 * pure balance score in (0, 1], equal to 1.0 for perfectly balanced groups regardless of sigma or
 * player count.
 */
@UtilityClass
class MatchQualityCalculator {

    private static final GameInfo GAME_INFO = GameInfo.getDefaultGameInfo();
    private static final FactorGraphTrueSkillCalculator CALCULATOR = new FactorGraphTrueSkillCalculator();
    // Guards the normalization against ideal-quality underflow for very large groups.
    private static final double MIN_IDEAL_QUALITY = 1.0e-9;

    record Result(double raw, double normalized) {}

    static Result calculate(
            List<MatchmakingQueueMember> members, Map<MatchmakingQueueMember, PlayerMatchmakingData> matchmakingData) {
        double raw = quality(members, matchmakingData, false);
        double ideal = quality(members, matchmakingData, true);
        double normalized = Math.min(1.0, raw / Math.max(ideal, MIN_IDEAL_QUALITY));
        return new Result(raw, normalized);
    }

    private static double quality(
            List<MatchmakingQueueMember> members,
            Map<MatchmakingQueueMember, PlayerMatchmakingData> matchmakingData,
            boolean equalizeMeans) {
        List<ITeam> teams = new ArrayList<>();
        for (MatchmakingQueueMember member : members) {
            PlayerMatchmakingData data = matchmakingData.get(member);
            Rating rating = equalizeMeans
                    ? new Rating(
                            GAME_INFO.getDefaultRating().getMean(),
                            data.rating().getStandardDeviation())
                    : data.rating();
            Team team = new Team();
            team.addPlayer(new Player<>(data.userId()), rating);
            teams.add(team);
        }
        return CALCULATOR.calculateMatchQuality(GAME_INFO, teams);
    }
}
