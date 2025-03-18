package ti4.service.statistics;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.map.manage.ManagedPlayer;
import ti4.message.MessageHelper;

@UtilityClass
public class AverageTurnTimeService {

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(
            new StatisticsPipeline.StatisticsEvent("getAverageTurnTime", event, () -> getAverageTurnTime(event)));
    }

    private void getAverageTurnTime(SlashCommandInteractionEvent event) {
        Map<String, Map.Entry<Integer, Long>> playerTurnTimes = new HashMap<>();
        Map<String, Set<Long>> playerAverageTurnTimes = new HashMap<>();

        boolean ignoreEndedGames = event.getOption(Constants.IGNORE_ENDED_GAMES, false, OptionMapping::getAsBoolean);
        boolean showMedian = event.getOption(Constants.SHOW_MEDIAN, false, OptionMapping::getAsBoolean);
        Predicate<Game> endedGamesFilter = ignoreEndedGames ? m -> !m.isHasEnded() : m -> true;

        GamesPage.consumeAllGames(
            endedGamesFilter,
            game -> getAverageTurnTimeForGame(game, playerTurnTimes, playerAverageTurnTimes));

        HashMap<String, Long> playerMedianTurnTimes = playerAverageTurnTimes.entrySet().stream()
            .map(e -> Map.entry(e.getKey(), Helper.median(e.getValue().stream().sorted().toList())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldEntry, newEntry) -> oldEntry, HashMap::new));

        StringBuilder sb = new StringBuilder("## __**Average Turn Time:**__\n");

        int index = 1;
        Comparator<Map.Entry<String, Map.Entry<Integer, Long>>> comparator = (o1, o2) -> {
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
        List<Map.Entry<String, Map.Entry<Integer, Long>>> turnTimes = playerTurnTimes.entrySet().stream()
            .filter(o -> o.getValue().getValue() != 0 && o.getValue().getKey() > minimumTurnsToShow)
            .sorted(comparator)
            .limit(topLimit)
            .toList();

        for (var userTurnCountTotalTime : turnTimes) {
            var user = GameManager.getManagedPlayer(userTurnCountTotalTime.getKey());
            int turnCount = userTurnCountTotalTime.getValue().getKey();
            long totalMillis = userTurnCountTotalTime.getValue().getValue();

            if (user == null || turnCount == 0 || totalMillis == 0) continue;

            long averageTurnTime = totalMillis / turnCount;

            sb.append("`").append(Helper.leftpad(String.valueOf(index), 3)).append(". ");
            sb.append(DateTimeHelper.getTimeRepresentationToSeconds(averageTurnTime));
            if (showMedian) sb.append(" (median: ").append(DateTimeHelper.getTimeRepresentationToSeconds(playerMedianTurnTimes.get(userTurnCountTotalTime.getKey()))).append(")");
            sb.append("` ").append(user.getName());
            sb.append("   [").append(turnCount).append(" total turns]");
            sb.append("\n");
            index++;
        }

        MessageHelper.sendMessageToThread(event.getChannel(), "Average Turn Time", sb.toString());
    }

    private static void getAverageTurnTimeForGame(
        Game game, Map<String, Map.Entry<Integer, Long>> playerTurnTimes,
        Map<String, Set<Long>> playerAverageTurnTimes
    ) {
        for (Player player : game.getRealPlayers()) {
            Integer totalTurns = player.getNumberOfTurns();
            Long totalTurnTime = player.getTotalTurnTime();
            Map.Entry<Integer, Long> playerTurnTime = Map.entry(totalTurns, totalTurnTime);
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

    String getAverageTurnTime(List<User> users) {
        List<ManagedGame> userGames = users.stream()
            .map(user -> GameManager.getManagedPlayer(user.getId()))
            .filter(Objects::nonNull)
            .map(ManagedPlayer::getGames)
            .flatMap(Collection::stream)
            .distinct()
            .toList();

        Map<String, Map.Entry<Integer, Long>> playerTurnTimes = new HashMap<>();
        for (ManagedGame game : userGames) {
            getAverageTurnTimeForGame(game.getGame(), playerTurnTimes, new HashMap<>());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## __**Average Turn Time:**__\n");
        int index = 1;
        for (User user : users) {
            if (!playerTurnTimes.containsKey(user.getId())) {
                continue;
            }
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
}
