package ti4.service.statistics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.statistics.GameStatisticsFilterer;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class PlayerStatisticsService {

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(new StatisticsPipeline.StatisticsEvent(event, () -> getPlayerStatistics(event)));
    }

    private void getPlayerStatistics(SlashCommandInteractionEvent event) {
        String statisticToShow = event.getOption(Constants.PLAYER_STATISTIC, null, OptionMapping::getAsString);
        PlayerStatTypes stat = PlayerStatTypes.fromString(statisticToShow);
        if (stat == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown Statistic: " + statisticToShow);
            return;
        }
        switch (stat) {
            case PLAYER_WIN_PERCENT -> showPlayerWinPercent(event);
            case PLAYER_GAME_COUNT -> showPlayerGameCount(event);
            default -> MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown Statistic: " + statisticToShow);
        }
    }

    private void showPlayerGameCount(SlashCommandInteractionEvent event) {
        List<Game> filteredGames = GameStatisticsFilterer.getGamesFilter(event);
        Map<String, Integer> playerGameCount = new HashMap<>();
        Map<String, String> playerUserIdToUsername = new HashMap<>();
        for (Game game : filteredGames) {
            game.getRealPlayers().forEach(player -> {
                String userId = player.getUserID();
                playerUserIdToUsername.put(userId, player.getUserName());
                playerGameCount.put(userId,
                    1 + playerGameCount.getOrDefault(userId, 0));
            });
        }

        int maximumListedPlayers = event.getOption("max_list_size", 50, OptionMapping::getAsInt);
        int minimumGameCountFilter = event.getOption("has_minimum_game_count", 10, OptionMapping::getAsInt);
        List<Map.Entry<String, Integer>> entries = playerUserIdToUsername.keySet().stream()
            .filter(userId -> playerGameCount.get(userId) >= minimumGameCountFilter)
            .map(userId -> Map.entry(userId, playerGameCount.get(userId)))
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("__**Player Game Count:**__").append("\n");
        if (entries.isEmpty()) {
            sb.append("No players found for the given filters!");
        }
        for (int i = 0; i < entries.size() && i < maximumListedPlayers; i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            sb.append(i + 1)
                .append(". `")
                .append(StringUtils.leftPad(playerUserIdToUsername.get(entry.getKey()), 4))
                .append("` ")
                .append(entry.getValue())
                .append(" games")
                .append("\n");
        }

        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Player Game Count", sb.toString());
    }

    private static void showPlayerWinPercent(SlashCommandInteractionEvent event) {
        List<Game> filteredGames = GameStatisticsFilterer.getGamesFilter(event);
        Map<String, Integer> playerWinCount = new HashMap<>();
        Map<String, Integer> playerGameCount = new HashMap<>();
        Map<String, String> playerUserIdToUsername = new HashMap<>();
        for (Game game : filteredGames) {
            Optional<Player> winner = game.getWinner();
            if (winner.isEmpty()) {
                continue;
            }
            String winningUserId = winner.get().getUserID();
            playerWinCount.put(winningUserId,
                1 + playerWinCount.getOrDefault(winningUserId, 0));

            game.getRealPlayers().forEach(player -> {
                String userId = player.getUserID();
                playerUserIdToUsername.put(userId, player.getUserName());
                playerGameCount.put(userId,
                    1 + playerGameCount.getOrDefault(userId, 0));
            });
        }

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
}
