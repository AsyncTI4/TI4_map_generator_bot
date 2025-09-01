package ti4.service.statistics.matchmaking;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.IPlayer;
import de.gesundkrank.jskills.ITeam;
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Rating;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.trueskill.FactorGraphTrueSkillCalculator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
class MatchmakingRatingService {

    private static final double SIGMA_CALIBRATION_THRESHOLD = 1;

    static List<MatchmakingRating> calculateRatings(List<MatchmakingGame> games) {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        Map<IPlayer, Rating> ratings = new HashMap<>();
        Map<MatchmakingPlayer, Player<String>> players = new HashMap<>();

        games.sort(Comparator.comparingLong(MatchmakingGame::endedDate).thenComparing(MatchmakingGame::name));

        var calculator = new FactorGraphTrueSkillCalculator();
        for (MatchmakingGame game : games) {
            List<MatchmakingPlayer> gamePlayers = game.players();
            var teams = new ArrayList<ITeam>();
            int[] ranks = new int[gamePlayers.size()];
            for (int i = 0; i < gamePlayers.size(); i++) {
                MatchmakingPlayer gamePlayer = gamePlayers.get(i);
                var tsPlayer = players.computeIfAbsent(gamePlayer, k -> new Player<>(gamePlayer.userId()));
                Rating rating = ratings.computeIfAbsent(tsPlayer, id -> gameInfo.getDefaultRating());
                var team = new Team();
                team.addPlayer(tsPlayer, rating);
                teams.add(team);
                ranks[i] = gamePlayer.rank();
            }
            Map<IPlayer, Rating> newRatings = calculator.calculateNewRatings(gameInfo, teams, ranks);
            ratings.putAll(newRatings);
        }

        return buildPlayerRatings(players, ratings);
    }

    private static List<MatchmakingRating> buildPlayerRatings(
            Map<MatchmakingPlayer, Player<String>> players, Map<IPlayer, Rating> ratings) {
        return players.entrySet().stream()
                .map(entry -> {
                    Rating rating = ratings.get(entry.getValue());
                    double calibrationPercent =
                            Math.min(100, SIGMA_CALIBRATION_THRESHOLD / rating.getStandardDeviation() * 100);
                    return new MatchmakingRating(entry.getKey(), entry.getKey().username(), rating.getMean(), calibrationPercent);
                })
                .sorted(Comparator.comparing(MatchmakingRating::rating).reversed())
                .toList();
    }
}
