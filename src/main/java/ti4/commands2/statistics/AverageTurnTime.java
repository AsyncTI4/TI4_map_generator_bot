package ti4.commands2.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

class AverageTurnTime extends Subcommand {

    public AverageTurnTime() {
        super(Constants.AVERAGE_TURN_TIME, "Average turn time across all games for all players");
        addOptions(new OptionData(OptionType.INTEGER, Constants.TOP_LIMIT, "How many players to show (Default = 50)"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.MINIMUM_NUMBER_OF_TURNS, "Minimum number of turns to show (Default = 1)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IGNORE_ENDED_GAMES, "True to exclude ended games from the calculation (default = false)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_MEDIAN, "True to also show median next to average (default = false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String text = getAverageTurnTimeText(event);
        MessageHelper.sendMessageToThread(event.getChannel(), "Average Turn Time", text);
    }

    private String getAverageTurnTimeText(SlashCommandInteractionEvent event) {
        Map<String, Entry<Integer, Long>> playerTurnTimes = new HashMap<>();
        Map<String, Set<Long>> playerAverageTurnTimes = new HashMap<>();

        boolean ignoreEndedGames = event.getOption(Constants.IGNORE_ENDED_GAMES, false, OptionMapping::getAsBoolean);
        boolean showMedian = event.getOption(Constants.SHOW_MEDIAN, false, OptionMapping::getAsBoolean);
        Predicate<Game> endedGamesFilter = ignoreEndedGames ? m -> !m.isHasEnded() : m -> true;

        for (Game game : GameManager.getGameNameToGame().values().stream().filter(endedGamesFilter).toList()) {
            mapPlayerTurnTimes(playerTurnTimes, playerAverageTurnTimes, game);
        }

        HashMap<String, Long> playerMedianTurnTimes = playerAverageTurnTimes.entrySet().stream().map(e -> Map.entry(e.getKey(), Helper.median(e.getValue().stream().sorted().toList()))).collect(Collectors.toMap(Entry::getKey, Entry::getValue, (oldEntry, newEntry) -> oldEntry, HashMap::new));
        StringBuilder sb = new StringBuilder();

        sb.append("## __**Average Turn Time:**__\n");

        int index = 1;
        Comparator<Entry<String, Entry<Integer, Long>>> comparator = (o1, o2) -> {
            int o1TurnCount = o1.getValue().getKey();
            int o2TurnCount = o2.getValue().getKey();
            long o1total = o1.getValue().getValue();
            long o2total = o2.getValue().getValue();
            if (o1TurnCount == 0 || o2TurnCount == 0) return -1;

            Long total1 = o1total / o1TurnCount;
            Long total2 = o2total / o2TurnCount;
            return total1.compareTo(total2);
        };

        int topLimit = event.getOption(Constants.TOP_LIMIT, 50, OptionMapping::getAsInt);
        int minimumTurnsToShow = event.getOption(Constants.MINIMUM_NUMBER_OF_TURNS, 1, OptionMapping::getAsInt);
        for (Entry<String, Entry<Integer, Long>> userTurnCountTotalTime : playerTurnTimes.entrySet().stream().filter(o -> o.getValue().getValue() != 0 && o.getValue().getKey() > minimumTurnsToShow).sorted(comparator).limit(topLimit).toList()) {
            User user = AsyncTI4DiscordBot.jda.getUserById(userTurnCountTotalTime.getKey());
            int turnCount = userTurnCountTotalTime.getValue().getKey();
            long totalMillis = userTurnCountTotalTime.getValue().getValue();

            if (user == null || turnCount == 0 || totalMillis == 0) continue;

            long averageTurnTime = totalMillis / turnCount;

            sb.append("`").append(Helper.leftpad(String.valueOf(index), 3)).append(". ");
            sb.append(DateTimeHelper.getTimeRepresentationToSeconds(averageTurnTime));
            if (showMedian) sb.append(" (median: ").append(DateTimeHelper.getTimeRepresentationToSeconds(playerMedianTurnTimes.get(userTurnCountTotalTime.getKey()))).append(")");
            sb.append("` ").append(user.getEffectiveName());
            sb.append("   [").append(turnCount).append(" total turns]");
            sb.append("\n");
            index++;
        }

        return sb.toString();
    }

    private void mapPlayerTurnTimes(Map<String, Entry<Integer, Long>> playerTurnTimes, Map<String, Set<Long>> playerAverageTurnTimes, Game game) {
        for (Player player : game.getRealPlayers()) {
            Integer totalTurns = player.getNumberTurns();
            Long totalTurnTime = player.getTotalTurnTime();
            Entry<Integer, Long> playerTurnTime = Map.entry(totalTurns, totalTurnTime);
            playerTurnTimes.merge(player.getUserID(), playerTurnTime,
                (oldEntry, newEntry) -> Map.entry(oldEntry.getKey() + playerTurnTime.getKey(), oldEntry.getValue() + playerTurnTime.getValue()));

            if (playerTurnTime.getKey() == 0) continue;
            Long averageTurnTime = playerTurnTime.getValue() / playerTurnTime.getKey();
            playerAverageTurnTimes.compute(player.getUserID(), (key, value) -> {
                if (value == null) value = new HashSet<>();
                value.add(averageTurnTime);
                return value;
            });
        }
    }

    public String getSelectUsersTurnTimes(List<User> users, Map<String, Entry<Integer, Long>> playerTurnTimes) {
        StringBuilder sb = new StringBuilder();

        sb.append("## __**Average Turn Time:**__\n");
        int index = 1;
        for (User user : users) {
            int turnCount = playerTurnTimes.get(user.getId()).getKey();
            long totalMillis = playerTurnTimes.get(user.getId()).getValue();

            if (turnCount == 0 || totalMillis == 0) continue;

            long averageTurnTime = totalMillis / turnCount;

            sb.append("`").append(Helper.leftpad(String.valueOf(index), 3)).append(". ");
            sb.append(DateTimeHelper.getTimeRepresentationToSeconds(averageTurnTime));
            sb.append("` ").append(user.getEffectiveName());
            sb.append("   [").append(turnCount).append(" total turns]");
            sb.append("\n");
            index++;
        }
        return sb.toString();
    }

    public Map<String, Entry<Integer, Long>> getAllPlayersTurnTimes(boolean ignoreEndedGames) {
        Predicate<Game> endedGamesFilter = ignoreEndedGames ? m -> !m.isHasEnded() : m -> true;
        Map<String, Entry<Integer, Long>> playerTurnTimes = new HashMap<>();
        Map<String, Set<Long>> playerAverageTurnTimes = new HashMap<>();
        for (Game game : GameManager.getGameNameToGame().values().stream().filter(endedGamesFilter).toList()) {
            mapPlayerTurnTimes(playerTurnTimes, playerAverageTurnTimes, game);
        }
        return playerTurnTimes;
    }
}
