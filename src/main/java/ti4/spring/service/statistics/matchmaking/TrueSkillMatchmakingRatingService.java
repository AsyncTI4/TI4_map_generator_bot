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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
class TrueSkillMatchmakingRatingService {

    private static final FactorGraphTrueSkillCalculator CALCULATOR = new FactorGraphTrueSkillCalculator();

    private static final double SIGMA_CALIBRATION_THRESHOLD = 1.7;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int MINIMUM_GAMES_FOR_RANKING = 3;

    static final int RECENT_GAMES_WINDOW = 10;

    static List<MatchmakingRating> calculateRatings(List<MatchmakingGame> games, boolean useConservativeRating) {
        games.sort(Comparator.comparingLong(MatchmakingGame::endedDate).thenComparing(MatchmakingGame::name));

        GameInfo gameInfo = MatchmakingGameInfo.create();
        Map<String, Player<String>> userIdToTrueSkillPlayer = new HashMap<>();
        Map<IPlayer, Rating> trueSkillPlayerToRating = new HashMap<>();
        Map<String, String> userIdToUsername = new HashMap<>();
        Map<String, Integer> gamesPlayedByUserId = new HashMap<>();
        Map<String, Long> lastGameEndedDateByUserId = new HashMap<>();
        Map<String, Deque<Rating>> recentRatingsByUserId = new HashMap<>();

        for (MatchmakingGame game : games) {
            List<MatchmakingPlayer> gamePlayers = game.players();
            var teams = new ArrayList<ITeam>();
            int[] ranks = new int[gamePlayers.size()];
            for (int i = 0; i < gamePlayers.size(); i++) {
                MatchmakingPlayer gamePlayer = gamePlayers.get(i);
                var trueSkillPlayer = userIdToTrueSkillPlayer.computeIfAbsent(
                        gamePlayer.userId(), _ -> new Player<>(gamePlayer.userId()));
                Rating rating =
                        trueSkillPlayerToRating.computeIfAbsent(trueSkillPlayer, _ -> gameInfo.getDefaultRating());
                userIdToUsername.put(gamePlayer.userId(), gamePlayer.username());
                gamesPlayedByUserId.merge(gamePlayer.userId(), 1, Integer::sum);
                lastGameEndedDateByUserId.merge(gamePlayer.userId(), game.endedDate(), Math::max);

                var team = new Team();
                team.addPlayer(trueSkillPlayer, rating);
                teams.add(team);
                ranks[i] = gamePlayer.rank();
            }

            Map<IPlayer, Rating> newRatings = CALCULATOR.calculateNewRatings(gameInfo, teams, ranks);
            trueSkillPlayerToRating.putAll(newRatings);
            recordRecentRatings(gamePlayers, userIdToTrueSkillPlayer, trueSkillPlayerToRating, recentRatingsByUserId);
        }

        return buildMatchmakingRatings(
                userIdToTrueSkillPlayer,
                trueSkillPlayerToRating,
                userIdToUsername,
                gamesPlayedByUserId,
                lastGameEndedDateByUserId,
                recentRatingsByUserId,
                useConservativeRating);
    }

    private static void recordRecentRatings(
            List<MatchmakingPlayer> gamePlayers,
            Map<String, Player<String>> userIdToTrueSkillPlayer,
            Map<IPlayer, Rating> trueSkillPlayerToRating,
            Map<String, Deque<Rating>> recentRatingsByUserId) {
        for (MatchmakingPlayer gamePlayer : gamePlayers) {
            Player<String> trueSkillPlayer = userIdToTrueSkillPlayer.get(gamePlayer.userId());
            Deque<Rating> recentRatings =
                    recentRatingsByUserId.computeIfAbsent(gamePlayer.userId(), _ -> new ArrayDeque<>());
            recentRatings.addLast(trueSkillPlayerToRating.get(trueSkillPlayer));
            if (recentRatings.size() > RECENT_GAMES_WINDOW + 1) {
                recentRatings.removeFirst();
            }
        }
    }

    private static List<MatchmakingRating> buildMatchmakingRatings(
            Map<String, Player<String>> players,
            Map<IPlayer, Rating> ratings,
            Map<String, String> userIdToUsername,
            Map<String, Integer> gamesPlayedByUserId,
            Map<String, Long> lastGameEndedDateByUserId,
            Map<String, Deque<Rating>> recentRatingsByUserId,
            boolean useConservativeRating) {
        return players.entrySet().stream()
                .filter(entry -> gamesPlayedByUserId.getOrDefault(entry.getKey(), 0) >= MINIMUM_GAMES_FOR_RANKING)
                .map(entry -> {
                    String userId = entry.getKey();
                    String username = userIdToUsername.get(userId);
                    Rating rating = ratings.get(entry.getValue());

                    BigDecimal standardDeviation = BigDecimal.valueOf(rating.getStandardDeviation());
                    BigDecimal calculatedPercent = BigDecimal.valueOf(SIGMA_CALIBRATION_THRESHOLD)
                            .divide(standardDeviation, MathContext.DECIMAL64)
                            .multiply(ONE_HUNDRED);
                    BigDecimal calibrationPercent = calculatedPercent.min(ONE_HUNDRED);
                    double rawRating = ratingValue(rating, useConservativeRating);
                    long lastGameEndedDate = lastGameEndedDateByUserId.getOrDefault(userId, 0L);
                    return new MatchmakingRating(
                            userId,
                            username,
                            BigDecimal.valueOf(rawRating),
                            standardDeviation,
                            calibrationPercent,
                            lastGameEndedDate,
                            recentRatingDelta(recentRatingsByUserId.get(userId), rawRating, useConservativeRating));
                })
                .sorted(Comparator.comparing(MatchmakingRating::rating).reversed())
                .toList();
    }

    private static BigDecimal recentRatingDelta(
            Deque<Rating> recentRatings, double currentRating, boolean useConservativeRating) {
        if (recentRatings == null || recentRatings.size() < RECENT_GAMES_WINDOW + 1) {
            return null;
        }
        double ratingBeforeWindow = ratingValue(recentRatings.peekFirst(), useConservativeRating);
        return BigDecimal.valueOf(currentRating - ratingBeforeWindow);
    }

    private static double ratingValue(Rating rating, boolean useConservativeRating) {
        return useConservativeRating ? rating.getConservativeRating() : rating.getMean();
    }
}
