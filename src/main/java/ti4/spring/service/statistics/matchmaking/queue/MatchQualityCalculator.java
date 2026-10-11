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
import ti4.spring.service.statistics.matchmaking.MatchmakingGameInfo;

@UtilityClass
public class MatchQualityCalculator {

    private static final GameInfo GAME_INFO = MatchmakingGameInfo.create();
    private static final FactorGraphTrueSkillCalculator CALCULATOR = new FactorGraphTrueSkillCalculator();

    static double matchQuality(
            List<MatchmakingQueueMember> members, Map<MatchmakingQueueMember, PlayerMatchmakingData> matchmakingData) {
        return matchQuality(members.stream()
                .map(member -> matchmakingData.get(member).rating())
                .toList());
    }

    public static double matchQuality(List<Rating> ratings) {
        List<ITeam> teams = new ArrayList<>();
        for (int i = 0; i < ratings.size(); i++) {
            Team team = new Team();
            team.addPlayer(new Player<>(i), ratings.get(i));
            teams.add(team);
        }
        return CALCULATOR.calculateMatchQuality(GAME_INFO, teams);
    }
}
