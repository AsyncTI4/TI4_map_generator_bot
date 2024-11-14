package ti4.commands.statistics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PlayerStats extends StatisticsSubcommandData {

    private static final String MINIMUM_GAME_COUNT_FILTER = "has_minimum_game_count";
    private static final String MAX_LIST_SIZE = "max_list_size";

    public PlayerStats() {
        super("players", "Player Statistics");
        addOptions(new OptionData(OptionType.STRING, Constants.PLAYER_STATISTIC, "Choose a stat to show").setRequired(true).setAutoComplete(true));
        addOptions(GameStatisticFilterer.gameStatsFilters());
        addOptions(new OptionData(OptionType.INTEGER, MINIMUM_GAME_COUNT_FILTER, "Filter by the minimum number of games player has played, default 10"));
        addOptions(new OptionData(OptionType.INTEGER, MAX_LIST_SIZE, "The maximum number of players listed, default 50"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String statisticToShow = event.getOption(Constants.PLAYER_STATISTIC, null, OptionMapping::getAsString);
        PlayerStatistics stat = PlayerStatistics.fromString(statisticToShow);
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
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
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

        int maximumListedPlayers = event.getOption(MAX_LIST_SIZE, 50, OptionMapping::getAsInt);
        int minimumGameCountFilter = event.getOption(MINIMUM_GAME_COUNT_FILTER, 10, OptionMapping::getAsInt);
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
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
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

        int maximumListedPlayers = event.getOption(MAX_LIST_SIZE, 50, OptionMapping::getAsInt);
        int minimumGameCountFilter = event.getOption(MINIMUM_GAME_COUNT_FILTER, 10, OptionMapping::getAsInt);
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

    public enum PlayerStatistics {

        PLAYER_WIN_PERCENT("Player win percent", "Shows the win percent of each player rounded to the nearest integer"), //
        PLAYER_GAME_COUNT("Player game count", "Shows the number of games each player has played in");

        private final String name;
        private final String description;

        PlayerStatistics(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        public static PlayerStatistics fromString(String id) {
            for (PlayerStatistics stat : values()) {
                if (id.equals(stat.toString())) {
                    return stat;
                }
            }
            return null;
        }

        public String getAutoCompleteName() {
            return name + ": " + description;
        }

        public boolean search(String searchString) {
            return name.toLowerCase().contains(searchString) || description.toLowerCase().contains(searchString) || toString().contains(searchString);
        }
    }

}
