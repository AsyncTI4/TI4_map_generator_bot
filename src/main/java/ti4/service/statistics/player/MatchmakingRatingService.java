package ti4.service.statistics.player;

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
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
class MatchmakingRatingService {

    private static final int DEFAULT_PLAYER_LIST_SIZE = 50;

    static void calculateRatings(SlashCommandInteractionEvent event) {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        Map<IPlayer, Rating> ratings = new HashMap<>();
        Map<String, Player<String>> players = new HashMap<>();
        Map<String, Integer> playerGameCounts = new HashMap<>();

        List<Game> games = new ArrayList<>();
        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getFinishedGamesFilter(6, null),
                games::add);

        games.sort(Comparator.comparingLong(Game::getEndedDate));

        var calculator = new FactorGraphTrueSkillCalculator();
        for (Game game : games) {
            List<ti4.map.Player> gamePlayers = game.getRealAndEliminatedPlayers();
            var teams = new ArrayList<ITeam>();
            int[] ranks = new int[gamePlayers.size()];
            for (int i = 0; i < gamePlayers.size(); i++) {
                ti4.map.Player gamePlayer = gamePlayers.get(i);
                String userId = gamePlayer.getUserID();
                playerGameCounts.merge(userId, 1, Integer::sum);
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

        int minimumGameCount = event.getOption("has_minimum_game_count", 10, OptionMapping::getAsInt);
        playerGameCounts.entrySet().stream()
            .filter(entry -> entry.getValue() < minimumGameCount)
            .forEach(entry -> players.remove(entry.getKey()));

        sendMessage(event, players, ratings);
    }

    private static int getRank(Game game, ti4.map.Player player) {
        boolean isWinner = game.getWinners().stream()
            .anyMatch(w -> w.getUserID().equals(player.getUserID()));
        if (isWinner) {
            return 1;
        }
        if (game.getVp() - player.getTotalVictoryPoints() <= 3) {
            return 2;
        }
        return 3;
    }

    private static void sendMessage(SlashCommandInteractionEvent event, Map<String, Player<String>> players, Map<IPlayer, Rating> ratings) {
        List<PlayerRating> playerRatings = players.entrySet().stream()
            .map(entry -> {
                Rating rating = ratings.get(entry.getValue());
                String username = GameManager.getManagedPlayer(entry.getKey()).getName();
                return new PlayerRating(username, Math.round(rating.getConservativeRating()));

            })
            .sorted(Comparator.comparing(PlayerRating::rating).reversed())
            .toList();

        int maximumListedPlayers = event.getOption("max_list_size", DEFAULT_PLAYER_LIST_SIZE, OptionMapping::getAsInt);

        StringBuilder sb = new StringBuilder();
        sb.append("__**Player Matchmaking Ratings:**__\n");
        for (int i = 0; i < playerRatings.size() && i < maximumListedPlayers; i++) {
            var playerRating = playerRatings.get(i);
            sb.append(i + 1)
                .append(". `")
                .append(playerRating.username)
                .append("` `")
                .append(Math.round(playerRating.rating))
                .append("`\n");
        }
        MessageHelper.sendMessageToThread(
            (MessageChannelUnion) event.getMessageChannel(), "Player Matchmaking Ratings", sb.toString());
    }

    private record PlayerRating(String username, double rating) {}
}