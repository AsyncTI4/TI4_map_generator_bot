package ti4.spring.service.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.stereotype.Service;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.statistics.StatisticsPipeline;
import ti4.spring.context.SpringContext;

@Service
@RequiredArgsConstructor
public class AverageTurnTimeService {

    private final PlayerEntityRepository playerEntityRepository;

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> getAverageTurnTime(event));
    }

    private void getAverageTurnTime(SlashCommandInteractionEvent event) {
        boolean ignoreEndedGames = event.getOption(Constants.IGNORE_ENDED_GAMES, false, OptionMapping::getAsBoolean);
        boolean showMedian = event.getOption(Constants.SHOW_MEDIAN, false, OptionMapping::getAsBoolean);
        int topLimit = event.getOption(Constants.TOP_LIMIT, 50, OptionMapping::getAsInt);
        int minTurns = event.getOption(Constants.MINIMUM_NUMBER_OF_TURNS, 1, OptionMapping::getAsInt);

        Iterable<PlayerEntity> players = ignoreEndedGames
                ? playerEntityRepository.findAllPlayersOfActiveGames()
                : playerEntityRepository.findAll();

        Map<String, PlayerStatsAccumulator> statsMap = new HashMap<>();
        for (PlayerEntity player : players) {
            if (player.getTotalNumberOfTurns() == 0) {
                continue;
            }
            statsMap.computeIfAbsent(
                            player.getDiscordUserId(), id -> new PlayerStatsAccumulator(player.getDiscordUsername()))
                    .addGame(player.getTotalNumberOfTurns(), player.getTotalTurnTime());
        }

        List<PlayerStatsAccumulator> sortedResults = statsMap.values().stream()
                .filter(s -> s.totalTurns >= minTurns)
                .sorted(Comparator.comparingLong(PlayerStatsAccumulator::getAverage))
                .limit(topLimit)
                .toList();

        String result = toResultString(sortedResults, showMedian);

        MessageHelper.sendMessageToThread(event.getChannel(), "Average Turn Time", result);
    }

    private String toResultString(List<PlayerStatsAccumulator> sortedResults, boolean showMedian) {
        StringBuilder sb = new StringBuilder("## __**Average Turn Time (Database Stats):**__\n");
        int index = 1;
        for (var stats : sortedResults) {
            sb.append("`").append(Helper.leftpad(String.valueOf(index), 3)).append(". ");
            index++;
            sb.append(DateTimeHelper.getTimeRepresentationToSeconds(stats.getAverage()));

            if (showMedian) {
                long median = Helper.median(stats.gameAverages.stream().sorted().toList());
                sb.append(" (median: ")
                    .append(DateTimeHelper.getTimeRepresentationToSeconds(median))
                    .append(")");
            }

            sb.append("` ")
                .append(stats.username)
                .append("   [")
                .append(stats.totalTurns)
                .append(" total turns]\n");
        }
        return sb.toString();
    }

    private static class PlayerStatsAccumulator {
        String username;
        int totalTurns;
        long totalTime;
        List<Long> gameAverages = new ArrayList<>();

        PlayerStatsAccumulator(String username) {
            this.username = username;
        }

        void addGame(int turns, long time) {
            totalTurns += turns;
            totalTime += time;
            gameAverages.add(time / turns);
        }

        long getAverage() {
            return totalTurns == 0 ? 0 : totalTime / totalTurns;
        }
    }

    public static AverageTurnTimeService getBean() {
        return SpringContext.getBean(AverageTurnTimeService.class);
    }
}
