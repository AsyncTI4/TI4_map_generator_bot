package ti4.service.statistics.player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
class PlayerExpectedWinDeltaStatisticsService {

    static void showPlayerExpectedWinDelta(SlashCommandInteractionEvent event) {
        Map<String, Double> playerWinCount = new HashMap<>();
        Map<String, Double> playerExpectedWinCount = new HashMap<>();
        Map<String, Integer> playerGameCount = new HashMap<>();
        Map<String, String> playerUserIdToUsername = new HashMap<>();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event),
                game -> calculate(
                        game, playerWinCount, playerExpectedWinCount, playerGameCount, playerUserIdToUsername));

        int maximumListedPlayers = event.getOption("max_list_size", 50, OptionMapping::getAsInt);
        int minimumGameCountFilter = event.getOption("min_game_count", 10, OptionMapping::getAsInt);
        List<Map.Entry<String, Double>> entries = playerUserIdToUsername.keySet().stream()
                .filter(userId -> playerGameCount.get(userId) >= minimumGameCountFilter)
                .map(userId -> Map.entry(
                        userId,
                        getPerformance(
                                playerWinCount.getOrDefault(userId, 0.0),
                                playerExpectedWinCount.getOrDefault(userId, 0.0))))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("__**Player Win Performance (vs expected):**__\n");
        if (entries.isEmpty()) {
            sb.append("No players found for the given filters!");
        }
        for (int i = 0; i < entries.size() && i < maximumListedPlayers; i++) {
            Map.Entry<String, Double> entry = entries.get(i);
            sb.append(i + 1)
                    .append(". `")
                    .append(StringUtils.leftPad(playerUserIdToUsername.get(entry.getKey()), 4))
                    .append("` `")
                    .append(StringUtils.leftPad(String.format("%+.2f", entry.getValue()), 7))
                    .append("%` (")
                    .append(playerGameCount.get(entry.getKey()))
                    .append(" games)\n");
        }

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Player Win Performance", sb.toString());
    }

    private static double getPerformance(double actualWins, double expectedWins) {
        return expectedWins == 0 ? 0 : ((actualWins / expectedWins) - 1) * 100;
    }

    private static void calculate(
            Game game,
            Map<String, Double> playerWinCount,
            Map<String, Double> playerExpectedWinCount,
            Map<String, Integer> playerGameCount,
            Map<String, String> playerUserIdToUsername) {
        if (game.getWinners().isEmpty()) {
            return;
        }

        int playerCount = game.getRealAndEliminatedPlayers().size();
        if (playerCount == 0) {
            return;
        }
        double expectedWinPerPlayer = 1.0 / playerCount;

        for (Player winner : game.getWinners()) {
            String winningUserId = winner.getStatsTrackedUserID();
            playerWinCount.put(winningUserId, 1 + playerWinCount.getOrDefault(winningUserId, 0.0));
        }

        for (Player player : game.getRealAndEliminatedPlayers()) {
            String userId = player.getStatsTrackedUserID();
            playerUserIdToUsername.put(userId, player.getStatsTrackedUserName());
            playerGameCount.put(userId, 1 + playerGameCount.getOrDefault(userId, 0));
            playerExpectedWinCount.put(userId, expectedWinPerPlayer + playerExpectedWinCount.getOrDefault(userId, 0.0));
        }
    }
}
