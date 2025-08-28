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

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> calculateRatings(event));
    }

    private static void calculateRatings(SlashCommandInteractionEvent event) {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        Map<IPlayer, Rating> ratings = new HashMap<>();
        Map<String, Player<String>> players = new HashMap<>();

        List<Game> games = new ArrayList<>();
        GamesPage.consumeAllGames(GameStatisticsFilterer.getFinishedGamesFilter(6, null), games::add);
        games = games.stream()
                .filter(not(Game::isAllianceMode))
                .sorted(Comparator.comparingLong(Game::getEndedDate))
                .toList();

        var calculator = new FactorGraphTrueSkillCalculator();
        for (Game game : games) {
            List<ti4.map.Player> gamePlayers = game.getRealAndEliminatedPlayers();
            var teams = new ArrayList<ITeam>();
            int[] ranks = new int[gamePlayers.size()];
            for (int i = 0; i < gamePlayers.size(); i++) {
                ti4.map.Player gamePlayer = gamePlayers.get(i);
                String userId = gamePlayer.getUserID();
                var tsPlayer = players.computeIfAbsent(userId, Player::new);
                Rating rating = ratings.computeIfAbsent(tsPlayer, id -> gameInfo.getDefaultRating());
                var team = new Team();
                team.addPlayer(tsPlayer, rating);
                teams.add(team);
                ranks[i] = getRank(game, gamePlayer);
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
                    double conservativeRating = rating.getConservativeRating();
                    Confidence confidence = Confidence.calculate(rating);
                    return new PlayerRating(username, conservativeRating, confidence);
                })
                .filter(rating -> rating.confidence.ordinal() >= Confidence.HIGH.ordinal())
                .sorted(Comparator.comparing(PlayerRating::rating).reversed())
                .toList();
    }

    private static int getRank(Game game, ti4.map.Player player) {
        boolean isWinner =
                game.getWinners().stream().anyMatch(w -> w.getUserID().equals(player.getUserID()));
        if (isWinner) {
            return 1;
        }
        if (game.getVp() - player.getTotalVictoryPoints() <= 3) {
            return 2;
        }
        return 3;
    }

    private static void sendMessage(SlashCommandInteractionEvent event, List<PlayerRating> playerRatings) {
        StringBuilder sb = new StringBuilder();
        sb.append("__**Player Matchmaking Ratings:**__\n");
        for (int i = 0; i < playerRatings.size() && i < MAX_LIST_SIZE; i++) {
            var playerRating = playerRatings.get(i);
            sb.append(i + 1)
                    .append(". `")
                    .append(playerRating.username)
                    .append("` ")
                    .append("`Rating=")
                    .append(Math.round(playerRating.rating))
                    .append("`\n");
        }
        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Player Matchmaking Ratings", sb.toString());
    }

    private record PlayerRating(String username, double rating, Confidence confidence) {}

    private enum Confidence {
        VERY_LOW(1.4),
        LOW(1.2),
        MEDIUM(1.0),
        HIGH(0.8),
        VERY_HIGH(0.6);

        final double confidenceThreshold;

        Confidence(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }

        static Confidence calculate(Rating rating) {
            double standardDeviation = rating.getStandardDeviation();
            if (isInRange(VERY_HIGH, standardDeviation)) return VERY_HIGH;
            if (isInRange(HIGH, standardDeviation)) return HIGH;
            if (isInRange(MEDIUM, standardDeviation)) return MEDIUM;
            if (isInRange(LOW, standardDeviation)) return LOW;
            return VERY_LOW;
        }

        private static boolean isInRange(Confidence confidence, double standardDeviation) {
            return standardDeviation <= confidence.confidenceThreshold;
        }
    }
}
