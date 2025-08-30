package ti4.service.statistics;

import static java.util.function.Predicate.not;

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
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
public class MatchmakingRatingService {

    private static final int MAX_LIST_SIZE = 50;
    private static final double SIGMA_CALIBRATION_THRESHOLD = 1;

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> calculateRatings(event));
    }

    private static void calculateRatings(SlashCommandInteractionEvent event) {
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

        List<PlayerRating> playerRatings = buildPlayerRatings(players, ratings);
        sendMessage(event, playerRatings);
    }

    private static List<PlayerRating> buildPlayerRatings(
            Map<String, Player<String>> players, Map<IPlayer, Rating> ratings) {
        return players.entrySet().stream()
                .map(entry -> {
                    Rating rating = ratings.get(entry.getValue());
                    String username =
                            GameManager.getManagedPlayer(entry.getKey()).getName();
                    double calibrationPercent = SIGMA_CALIBRATION_THRESHOLD / rating.getStandardDeviation() * 100;
                    return new PlayerRating(entry.getKey(), username, rating.getMean(), calibrationPercent);
                })
                .sorted(Comparator.comparing(PlayerRating::rating).reversed())
                .toList();
    }

    private static void sendMessage(SlashCommandInteractionEvent event, List<PlayerRating> playerRatings) {
        int maxListSize = Math.min(MAX_LIST_SIZE, playerRatings.size());
        StringBuilder sb = new StringBuilder();
        sb.append("__**Player Matchmaking Ratings:**__\n");
        for (int i = 0, listSize = 0; i < playerRatings.size() && listSize < maxListSize; i++) {
            var playerRating = playerRatings.get(i);
            if (playerRating.calibrationPercent < 100) {
                continue;
            }
            listSize++;
            String formattedString =
                    String.format("%d. `%s` `Rating=%.3f`\n", listSize, playerRating.username, playerRating.rating);
            sb.append(formattedString);
        }

        double averageRating = playerRatings.stream()
                .mapToDouble(PlayerRating::rating)
                .average()
                .orElse(Double.NaN);
        String formattedString = String.format(
                """

                This list only includes the top %d players with a high confidence in their rating.

                The average rating of the player base is `%.3f`
                """,
                maxListSize, averageRating);
        sb.append(formattedString);

        playerRatings.stream()
                .filter(playerRating ->
                        playerRating.userId.equals(event.getUser().getId()))
                .findFirst()
                .ifPresent(playerRating -> sb.append(String.format(
                        "\nWe are `%.1f%%` of the way to a high confidence in your rating.",
                        Math.min(100, playerRating.calibrationPercent))));

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Player Matchmaking Ratings", sb.toString());
    }

    private record PlayerRating(String userId, String username, double rating, double calibrationPercent) {}

    private record MatchmakingGame(long endedDate, int vp, List<MatchmakingPlayer> players, Set<String> winners) {

        static MatchmakingGame from(Game game) {
            Set<String> winners =
                    game.getWinners().stream().map(ti4.map.Player::getUserID).collect(Collectors.toSet());
            List<MatchmakingPlayer> players = game.getRealAndEliminatedPlayers().stream()
                    .map(player -> {
                        int rank = winners.contains(player.getUserID())
                                ? 1
                                : game.getVp() - player.getTotalVictoryPoints() <= 3 ? 2 : 3;
                        return new MatchmakingPlayer(player.getUserID(), rank);
                    })
                    .toList();
            return new MatchmakingGame(game.getEndedDate(), game.getVp(), players, winners);
        }
    }

    private record MatchmakingPlayer(String userId, int rank) {}
}
