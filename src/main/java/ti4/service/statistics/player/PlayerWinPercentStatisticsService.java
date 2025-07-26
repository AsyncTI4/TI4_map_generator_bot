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
class PlayerWinPercentStatisticsService {

    static void showPlayerWinPercent(SlashCommandInteractionEvent event) {
        Map<String, Integer> playerWinCount = new HashMap<>();
        Map<String, Integer> playerGameCount = new HashMap<>();
        Map<String, String> playerUserIdToUsername = new HashMap<>();

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getGamesFilterForWonGame(event),
            game -> getPlayerWinPercent(game, playerWinCount, playerGameCount, playerUserIdToUsername)
        );

        int maximumListedPlayers = event.getOption("max_list_size", 50, OptionMapping::getAsInt);
        int minimumGameCountFilter = event.getOption("has_minimum_game_count", 10, OptionMapping::getAsInt);
        List<Map.Entry<String, Long>> entries = playerUserIdToUsername.keySet().stream()
            .filter(userId -> playerGameCount.get(userId) >= minimumGameCountFilter)
            .map(userId -> {
                double winCount = playerWinCount.getOrDefault(userId, 0);
                double gameCount = playerGameCount.get(userId);
                return Map.entry(userId, Math.round(100 * winCount / gameCount));
            })
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("__**Player Win Percent:**__").append("\n");
        if (entries.isEmpty()) {
            sb.append("No players found for the given filters!");
        }
        for (int i = 0; i < entries.size() && i < maximumListedPlayers; i++) {
            Map.Entry<String, Long> entry = entries.get(i);
            sb.append(i + 1)
                .append(". `")
                .append(StringUtils.leftPad(playerUserIdToUsername.get(entry.getKey()), 4))
                .append("` ")
                .append(entry.getValue())
                .append("% (")
                .append(playerGameCount.get(entry.getKey()))
                .append(" games) ")
                .append("\n");
        }

        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Player Win Percent", sb.toString());
    }

    private static void getPlayerWinPercent(Game game, Map<String, Integer> playerWinCount, Map<String, Integer> playerGameCount,
                                            Map<String, String> playerUserIdToUsername) {
        if (game.getWinners().isEmpty()) {
            return;
        }

        for (Player winner : game.getWinners()) {
            String winningUserId = winner.getUserID();
            playerWinCount.put(winningUserId, 1 + playerWinCount.getOrDefault(winningUserId, 0));
        }

        game.getRealPlayers().forEach(player -> {
            String userId = player.getUserID();
            playerUserIdToUsername.put(userId, player.getUserName());
            playerGameCount.put(userId, 1 + playerGameCount.getOrDefault(userId, 0));
        });
    }
}
