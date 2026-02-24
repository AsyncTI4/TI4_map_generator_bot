package ti4.spring.service.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;
import ti4.spring.persistence.PlayerEntity;
import ti4.spring.persistence.PlayerEntityRepository;
import ti4.spring.persistence.UserEntity;

@Service
@RequiredArgsConstructor
public class AverageTurnTimeService {

    private static final int DEFAULT_PLAYER_LIMIT = 50;
    private static final int DEFAULT_MINIMUM_NUMBER_OF_TURNS = 100;

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public void getAverageTurnTimes(SlashCommandInteractionEvent event) {
        boolean ignoreEndedGames = event.getOption(Constants.IGNORE_ENDED_GAMES, false, OptionMapping::getAsBoolean);
        boolean showMedian = event.getOption(Constants.SHOW_MEDIAN, false, OptionMapping::getAsBoolean);
        int topLimit = event.getOption(Constants.TOP_LIMIT, DEFAULT_PLAYER_LIMIT, OptionMapping::getAsInt);
        int minTurns = event.getOption(
                Constants.MINIMUM_NUMBER_OF_TURNS, DEFAULT_MINIMUM_NUMBER_OF_TURNS, OptionMapping::getAsInt);

        List<PlayerEntity> players = ignoreEndedGames
                ? playerEntityRepository.findAllPlayersOfActiveGames()
                : playerEntityRepository.findAll();

        List<UserAverageTurnTimeAccumulator> sortedResults = getAverageTurnTimes(players, minTurns, topLimit);

        String result = toResultString(sortedResults, showMedian);

        MessageHelper.sendMessageToThread(event.getChannel(), "Average Turn Time", result);
    }

    private List<UserAverageTurnTimeAccumulator> getAverageTurnTimes(
            List<PlayerEntity> players, int minTurns, int topLimit) {
        Map<UserEntity, UserAverageTurnTimeAccumulator> statsMap = new HashMap<>();
        for (PlayerEntity player : players) {
            if (player.getTotalNumberOfTurns() == 0) {
                continue;
            }
            statsMap.computeIfAbsent(
                            player.getUser(), user -> new UserAverageTurnTimeAccumulator(user.getId(), user.getName()))
                    .addGame(player.getTotalNumberOfTurns(), player.getTotalTurnTime());
        }

        return statsMap.values().stream()
                .filter(s -> s.totalTurns >= minTurns)
                .sorted(Comparator.comparingLong(UserAverageTurnTimeAccumulator::getAverage))
                .limit(topLimit)
                .toList();
    }

    private String toResultString(List<UserAverageTurnTimeAccumulator> sortedResults, boolean showMedian) {
        StringBuilder sb = new StringBuilder("## __**Average Turn Time:**__\n");
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

    public String getAverageTurnTimesString(List<String> userIds) {
        List<UserAverageTurnTimeAccumulator> averageTurnTimes = getAverageTurnTimes(userIds);
        return toResultString(averageTurnTimes, true);
    }

    private List<UserAverageTurnTimeAccumulator> getAverageTurnTimes(List<String> userIds) {
        List<PlayerEntity> players = playerEntityRepository.findAllPlayersForUsers(userIds);

        int minimumTurns = 0;
        int maximumResults = userIds.size();
        return getAverageTurnTimes(players, minimumTurns, maximumResults);
    }

    public Map<String, Long> getUserIdsToAverageTurnTimes(List<String> userIds) {
        List<UserAverageTurnTimeAccumulator> averageTurnTimes = getAverageTurnTimes(userIds);

        return averageTurnTimes.stream()
                .collect(Collectors.toMap(
                        acc -> acc.userId,
                        UserAverageTurnTimeAccumulator::getAverage,
                        (existing, replacement) -> existing));
    }

    public static AverageTurnTimeService getBean() {
        return SpringContext.getBean(AverageTurnTimeService.class);
    }

    private static class UserAverageTurnTimeAccumulator {
        String userId;
        String username;
        int totalTurns;
        long totalTime;
        List<Long> gameAverages = new ArrayList<>();

        UserAverageTurnTimeAccumulator(String userId, String username) {
            this.userId = userId;
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
}
