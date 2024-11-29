package ti4.service.statistics.player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
class PlayerGameCountStatisticsService {

    void showPlayerGameCount(SlashCommandInteractionEvent event) {
        Map<String, Integer> playerGameCount = new HashMap<>();
        Map<String, String> playerUserIdToUsername = new HashMap<>();

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getGamesFilter(event),
            game -> getPlayerGameCount(game, playerGameCount, playerUserIdToUsername)
        );

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

    private static void getPlayerGameCount(Game game, Map<String, Integer> playerGameCount, Map<String, String> playerUserIdToUsername) {
        game.getRealPlayers().forEach(player -> {
            String userId = player.getUserID();
            playerUserIdToUsername.put(userId, player.getUserName());
            playerGameCount.put(userId, 1 + playerGameCount.getOrDefault(userId, 0));
        });
    }
}
