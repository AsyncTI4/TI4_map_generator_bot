package ti4.service.statistics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.message.MessageHelper;

@UtilityClass
public class MedianTurnTimeService {

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(
            new StatisticsPipeline.StatisticsEvent("getMedianTurnTime", event, () -> getMedianTurnTime(event)));
    }

    private void getMedianTurnTime(SlashCommandInteractionEvent event) {
        Map<String, Integer> playerTurnCount = new HashMap<>();
        Map<String, Set<Long>> playerAverageTurnTimes = new HashMap<>();

        boolean ignoreEndedGames = event.getOption(Constants.IGNORE_ENDED_GAMES, false, OptionMapping::getAsBoolean);
        Predicate<Game> endedGamesFilter = ignoreEndedGames ? m -> !m.isHasEnded() : m -> true;

        GamesPage.consumeAllGames(
            endedGamesFilter,
            game -> getMedianTurnTimeForGame(game, playerTurnCount, playerAverageTurnTimes)
        );

        Map<String, Long> playerMedianTurnTimes = playerAverageTurnTimes.entrySet().stream()
            .map(e -> Map.entry(e.getKey(), Helper.median(e.getValue().stream().sorted().toList())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldEntry, newEntry) -> oldEntry, HashMap::new));

        StringBuilder sb = new StringBuilder("## __**Median Turn Time:**__\n");

        int index = 1;
        int minimumTurnsToShow = event.getOption(Constants.MINIMUM_NUMBER_OF_TURNS, 1, OptionMapping::getAsInt);

        int topLimit = event.getOption(Constants.TOP_LIMIT, 50, OptionMapping::getAsInt);
        List<Map.Entry<String, Long>> medianTurnTimes = playerMedianTurnTimes.entrySet().stream()
            .filter(o -> o.getValue() != 0 && playerTurnCount.get(o.getKey()) >= minimumTurnsToShow)
            .sorted(Map.Entry.comparingByValue())
            .limit(topLimit)
            .toList();

        for (var userMedianTurnTime : medianTurnTimes) {
            var user = GameManager.getManagedGame(userMedianTurnTime.getKey());
            long totalMillis = userMedianTurnTime.getValue();
            int turnCount = playerTurnCount.get(userMedianTurnTime.getKey());

            if (user == null || turnCount == 0 || totalMillis == 0) continue;

            sb.append("`").append(Helper.leftpad(String.valueOf(index), 3)).append(". ");
            sb.append(DateTimeHelper.getTimeRepresentationToSeconds(userMedianTurnTime.getValue()));
            sb.append("` ").append(user.getName());
            sb.append("   [").append(turnCount).append(" total turns]");
            sb.append("\n");
            index++;
        }

        MessageHelper.sendMessageToThread(event.getChannel(), "Median Turn Time", sb.toString());
    }

    private static void getMedianTurnTimeForGame(Game game, Map<String, Integer> playerTurnCount,
                                                    Map<String, Set<Long>> playerAverageTurnTimes) {
        for (Player player : game.getRealPlayers()) {
            int totalTurns = player.getNumberTurns();
            long totalTurnTime = player.getTotalTurnTime();
            if (totalTurns == 0 || totalTurnTime == 0) continue;

            Map.Entry<Integer, Long> playerTurnTime = Map.entry(totalTurns, totalTurnTime);
            Long averageTurnTime = playerTurnTime.getValue() / playerTurnTime.getKey();
            playerAverageTurnTimes.compute(player.getUserID(), (key, value) -> {
                if (value == null) value = new HashSet<>();
                value.add(averageTurnTime);
                return value;
            });
            playerTurnCount.merge(player.getUserID(), playerTurnTime.getKey(), Integer::sum);
        }
    }
}
