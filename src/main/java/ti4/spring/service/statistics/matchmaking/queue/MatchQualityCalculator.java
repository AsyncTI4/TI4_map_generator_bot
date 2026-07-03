package ti4.spring.service.statistics.matchmaking.queue;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.ITeam;
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.trueskill.FactorGraphTrueSkillCalculator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
class MatchQualityCalculator {

    private static final GameInfo GAME_INFO = GameInfo.getDefaultGameInfo();
    private static final FactorGraphTrueSkillCalculator CALCULATOR = new FactorGraphTrueSkillCalculator();

    static double matchQuality(
            List<MatchmakingQueueMember> members, Map<MatchmakingQueueMember, PlayerMatchmakingData> matchmakingData) {
        List<ITeam> teams = new ArrayList<>();
        for (MatchmakingQueueMember member : members) {
            PlayerMatchmakingData data = matchmakingData.get(member);
            Team team = new Team();
            team.addPlayer(new Player<>(data.userId()), data.rating());
            teams.add(team);
        }
        return CALCULATOR.calculateMatchQuality(GAME_INFO, teams);
    }
}
