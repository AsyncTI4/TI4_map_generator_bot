package ti4.spring.service.statistics.matchmaking;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.IPlayer;
import de.gesundkrank.jskills.ITeam;
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Rating;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.trueskill.FactorGraphTrueSkillCalculator;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
class TrueSkillMatchmakingRatingService {

    private static final double SIGMA_CALIBRATION_THRESHOLD = 1.5;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    static List<MatchmakingRating> calculateRatings(List<MatchmakingGame> games) {
        return calculateRatings(games, false);
    }

    static List<MatchmakingRating> calculateRatings(List<MatchmakingGame> games, boolean useConservativeRating) {
        games.sort(Comparator.comparingLong(MatchmakingGame::endedDate).thenComparing(MatchmakingGame::name));

        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        Map<String, Player<String>> userIdToTrueSkillPlayer = new HashMap<>();
        Map<IPlayer, Rating> trueSkillPlayerToRating = new HashMap<>();
        Map<String, String> userIdToUsername = new HashMap<>();

        for (MatchmakingGame game : games) {
            List<MatchmakingPlayer> gamePlayers = game.players();
            var teams = new ArrayList<ITeam>();
            int[] ranks = new int[gamePlayers.size()];
            for (int i = 0; i < gamePlayers.size(); i++) {
                MatchmakingPlayer gamePlayer = gamePlayers.get(i);
                var trueSkillPlayer = userIdToTrueSkillPlayer.computeIfAbsent(
                        gamePlayer.userId(), _ -> new Player<>(gamePlayer.userId()));
                Rating rating =
                        trueSkillPlayerToRating.computeIfAbsent(trueSkillPlayer, id -> gameInfo.getDefaultRating());
                userIdToUsername.put(gamePlayer.userId(), gamePlayer.username());

                var team = new Team();
                team.addPlayer(trueSkillPlayer, rating);
                teams.add(team);
                ranks[i] = gamePlayer.rank();
            }

            Map<IPlayer, Rating> newRatings =
                    new FactorGraphTrueSkillCalculator().calculateNewRatings(gameInfo, teams, ranks);
            trueSkillPlayerToRating.putAll(newRatings);
        }

        return buildMatchmakingRatings(
                userIdToTrueSkillPlayer, trueSkillPlayerToRating, userIdToUsername, useConservativeRating);
    }

    private static List<MatchmakingRating> buildMatchmakingRatings(
            Map<String, Player<String>> players,
            Map<IPlayer, Rating> ratings,
            Map<String, String> userIdToUsername,
            boolean useConservativeRating) {
        return players.entrySet().stream()
                .map(entry -> {
                    String userId = entry.getKey();
                    String username = userIdToUsername.get(userId);
                    Rating rating = ratings.get(entry.getValue());

                    BigDecimal standardDeviation = BigDecimal.valueOf(rating.getStandardDeviation());
                    BigDecimal calculatedPercent = BigDecimal.valueOf(SIGMA_CALIBRATION_THRESHOLD)
                            .divide(standardDeviation, MathContext.DECIMAL64)
                            .multiply(ONE_HUNDRED);
                    BigDecimal calibrationPercent = calculatedPercent.min(ONE_HUNDRED);
                    double rawRating = useConservativeRating ? rating.getConservativeRating() : rating.getMean();
                    return new MatchmakingRating(userId, username, BigDecimal.valueOf(rawRating), calibrationPercent);
                })
                .sorted(Comparator.comparing(MatchmakingRating::rating).reversed())
                .toList();
    }
}
