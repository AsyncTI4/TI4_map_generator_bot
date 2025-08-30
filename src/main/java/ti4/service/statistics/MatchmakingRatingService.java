package ti4.service.statistics;

import static java.util.function.Predicate.not;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.IPlayer;
import de.gesundkrank.jskills.ITeam;
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Rating;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.trueskill.FactorGraphTrueSkillCalculator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.GamesPage;

@UtilityClass
public class MatchmakingRatingService {

    public static final String MATCHMAKING_RATING_FILE = "matchmaking_ratings.json";
    private static final int MAX_LIST_SIZE = 50;
    private static final double SIGMA_CALIBRATION_THRESHOLD = 1;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.of("UTC"));

    public MatchmakingData calculateRatingsData() {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        Map<IPlayer, Rating> ratings = new HashMap<>();
        Map<String, Player<String>> players = new HashMap<>();

        List<MatchmakingGame> games = new ArrayList<>();
        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getFinishedGamesFilter(6, null).and(not(Game::isAllianceMode)),
            game -> games.add(MatchmakingGame.from(game)));
        games.sort(Comparator.comparingLong(MatchmakingGame::endedDate));

        var calculator = new FactorGraphTrueSkillCalculator();
        for (MatchmakingGame game : games) {
            List<MatchmakingPlayer> gamePlayers = game.players();
            var teams = new ArrayList<ITeam>();
            int[] ranks = new int[gamePlayers.size()];
            for (int i = 0; i < gamePlayers.size(); i++) {
                MatchmakingPlayer gamePlayer = gamePlayers.get(i);
                String userId = gamePlayer.userId();
                var tsPlayer = players.computeIfAbsent(userId, Player::new);
                Rating rating = ratings.computeIfAbsent(tsPlayer, id -> gameInfo.getDefaultRating());
                var team = new Team();
                team.addPlayer(tsPlayer, rating);
                teams.add(team);
                ranks[i] = gamePlayer.rank();
            }
            Map<IPlayer, Rating> newRatings = calculator.calculateNewRatings(gameInfo, teams, ranks);
            ratings.putAll(newRatings);
        }

        List<PlayerData> playerRatings = buildPlayerRatings(players, ratings);
        double averageRating = playerRatings.stream()
                .mapToDouble(PlayerData::rating)
                .average()
                .orElse(Double.NaN);
        return new MatchmakingData(playerRatings, averageRating, Instant.now());
    }

    private List<PlayerData> buildPlayerRatings(
            Map<String, Player<String>> players, Map<IPlayer, Rating> ratings) {
        return players.entrySet().stream()
                .map(entry -> {
                    Rating rating = ratings.get(entry.getValue());
                    String username = GameManager.getManagedPlayer(entry.getKey()).getName();
                    double calibrationPercent =
                            SIGMA_CALIBRATION_THRESHOLD / rating.getStandardDeviation() * 100;
                    return new PlayerData(entry.getKey(), username, rating.getMean(), calibrationPercent);
                })
                .sorted(Comparator.comparing(PlayerData::rating).reversed())
                .toList();
    }

    public String buildMessage(MatchmakingData data, String userId) {
        List<PlayerData> playerRatings = data.playerRatings();
        int maxListSize = Math.min(MAX_LIST_SIZE, playerRatings.size());
        StringBuilder sb = new StringBuilder();
        sb.append("__**Player Matchmaking Ratings:**__\n");
        for (int i = 0, listSize = 0; i < playerRatings.size() && listSize < maxListSize; i++) {
            var playerRating = playerRatings.get(i);
            if (playerRating.calibrationPercent < 100) {
                continue;
            }
            listSize++;
            sb.append(String.format(
                    "%d. `%s` `Rating=%.3f`\n", listSize, playerRating.username, playerRating.rating));
        }
        sb.append(String.format(
                "\nThis list only includes the top %d players with a high confidence in their rating.\n"
                        + "The average rating of the player base is `%.3f`\n",
                maxListSize, data.averageRating()));

        playerRatings.stream()
                .filter(pr -> pr.userId.equals(userId))
                .findFirst()
                .ifPresent(pr -> sb.append(String.format(
                        "We are `%.1f%%` of the way to a high confidence in your rating.\n",
                        Math.min(100, pr.calibrationPercent))));

        sb.append("Last calculated: ")
                .append(TIME_FORMATTER.format(data.lastCalculated()));

        return sb.toString();
    }

    private record PlayerData(String userId, String username, double rating, double calibrationPercent) {}

    public record MatchmakingData(
        List<PlayerData> playerRatings, double averageRating, Instant lastCalculated) {}


    private record PlayerRating(String userId, String username, double rating, double calibrationPercent) {}

    private record MatchmakingGame(long endedDate, List<MatchmakingPlayer> players) {

        static MatchmakingGame from(Game game) {
            List<MatchmakingPlayer> players = game.getRealAndEliminatedPlayers().stream()
                .map(player -> {
                    boolean isWinner = game.getWinners().stream()
                        .anyMatch(gamePlayer -> gamePlayer.getUserID().equals(player.getUserID()));
                    int rank = isWinner ? 1 : game.getVp() - player.getTotalVictoryPoints() <= 3 ? 2 : 3;
                    return new MatchmakingPlayer(player.getUserID(), rank);
                })
                .toList();
            return new MatchmakingGame(game.getEndedDate(), players);
        }
    }

    private record MatchmakingPlayer(String userId, int rank) {}
}
}
