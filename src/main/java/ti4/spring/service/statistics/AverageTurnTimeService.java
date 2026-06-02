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
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.PlayerEntityRepository;
import ti4.spring.service.persistence.UserEntity;

@Service
@RequiredArgsConstructor
public class AverageTurnTimeService {

    private static final int DEFAULT_PLAYER_LIMIT = 50;
    private static final int DEFAULT_MINIMUM_NUMBER_OF_TURNS = 100;

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public void getAverageTurnTimes(SlashCommandInteractionEvent event) {
        boolean ignoreEndedGames =
                event.getOption(Constants.IGNORE_ENDED_GAMES, Boolean.FALSE, OptionMapping::getAsBoolean);
        int topLimit = event.getOption(Constants.TOP_LIMIT, DEFAULT_PLAYER_LIMIT, OptionMapping::getAsInt);
        int minTurns = event.getOption(
                Constants.MINIMUM_NUMBER_OF_TURNS, DEFAULT_MINIMUM_NUMBER_OF_TURNS, OptionMapping::getAsInt);

        List<PlayerEntity> players = ignoreEndedGames
                ? playerEntityRepository.findAllWithUsersByActiveGame()
                : playerEntityRepository.findAllWithUsers();

        List<UserAverageTurnTimeAccumulator> sortedResults = getAverageTurnTimes(players, minTurns, topLimit);

        String result = toResultString(sortedResults);

        if (topLimit < 2000) {
            MessageHelper.sendMessageToThread(event.getChannel(), "Average Turn Time", result);
        } else {

            int times = 3;

            for (int i = 1; i < times; i++) {

                result = "";
                int slowUsers = 0;
                int usersUnder120Minutes = 0;
                int usersUnder90Minutes = 0;
                int usersUnder60Minutes = 0;
                int slowUsersWithFastGames = 0;
                int usersUnder120MinutesWithFastGames = 0;
                int usersUnder90MinutesWithFastGames = 0;
                int usersUnder60MinutesWithFastGames = 0;

                for (var stats : sortedResults) {
                    String userId = stats.userId;
                    List<Integer> threeFastestDays =
                            UserGameInfoService.get().getUsersThreeFastestDaysToComplete6PlayerGames(userId);
                    List<Integer> threeFastestDaysCopy = new ArrayList<>(threeFastestDays);
                    if (i == 1) {
                        if (threeFastestDaysCopy.size() > 3) {
                            threeFastestDaysCopy.removeLast();
                        }
                        if (threeFastestDaysCopy.size() > 3) {
                            threeFastestDaysCopy.removeLast();
                        }
                    }
                    boolean hasFastGames = threeFastestDaysCopy.stream().allMatch(days -> days < 11);
                    long averageTurnTime = DateTimeHelper.getTimeinMinutes(stats.getAverage());
                    if (averageTurnTime < 60) {
                        usersUnder60Minutes++;
                        if (hasFastGames) {
                            usersUnder60MinutesWithFastGames++;
                        }
                    } else if (averageTurnTime < 90) {
                        usersUnder90Minutes++;
                        if (hasFastGames) {
                            usersUnder90MinutesWithFastGames++;
                        }
                    } else if (averageTurnTime < 120) {
                        usersUnder120Minutes++;
                        if (hasFastGames) {
                            usersUnder120MinutesWithFastGames++;
                        }
                    } else {
                        slowUsers++;
                        if (hasFastGames) {
                            slowUsersWithFastGames++;
                        }
                    }
                }
                result += String.format(
                        "Users with average turn time under 60 minutes: %d (with fast games: %d)\n",
                        usersUnder60Minutes, usersUnder60MinutesWithFastGames);
                result += String.format(
                        "Users with average turn time under 90 minutes: %d (with fast games: %d)\n",
                        usersUnder90Minutes, usersUnder90MinutesWithFastGames);
                result += String.format(
                        "Users with average turn time under 120 minutes: %d (with fast games: %d)\n",
                        usersUnder120Minutes, usersUnder120MinutesWithFastGames);
                result += String.format(
                        "Users with average turn time over 120 minutes: %d (with fast games: %d)\n",
                        slowUsers, slowUsersWithFastGames);

                MessageHelper.sendMessageToThread(event.getChannel(), "Game Statistics " + i, result);
            }
        }
    }

    private static List<UserAverageTurnTimeAccumulator> getAverageTurnTimes(
            List<PlayerEntity> players, int minTurns, int topLimit) {
        Map<UserEntity, UserAverageTurnTimeAccumulator> statsMap = new HashMap<>();
        for (PlayerEntity player : players) {
            if (player.getTotalNumberOfTurns() == 0) continue;
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

    private static String toResultString(List<UserAverageTurnTimeAccumulator> sortedResults) {
        StringBuilder sb = new StringBuilder("## __**Average Turn Time:**__\n");
        int index = 1;
        for (var stats : sortedResults) {
            sb.append('`').append(Helper.leftpad(String.valueOf(index), 3)).append(". ");
            index++;
            sb.append(DateTimeHelper.getTimeRepresentationToSeconds(stats.getAverage()));

            sb.append("` ")
                    .append(stats.username)
                    .append("   [")
                    .append(stats.totalTurns)
                    .append(" total turns]\n");
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String getAverageTurnTimesString(List<String> userIds) {
        List<UserAverageTurnTimeAccumulator> averageTurnTimes = getAverageTurnTimes(userIds);
        return toResultString(averageTurnTimes);
    }

    private List<UserAverageTurnTimeAccumulator> getAverageTurnTimes(List<String> userIds) {
        List<PlayerEntity> players = playerEntityRepository.findAllWithUsersByUserIdIn(userIds);

        int minimumTurns = 0;
        int maximumResults = userIds.size();
        return getAverageTurnTimes(players, minimumTurns, maximumResults);
    }

    @Transactional(readOnly = true)
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
        private final String userId;
        private final String username;
        private int totalTurns;
        private long totalTime;
        private final List<Long> gameAverages = new ArrayList<>();

        UserAverageTurnTimeAccumulator(String userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        void addGame(int turns, long time) {
            totalTurns += turns;
            totalTime += time;
            if (turns > 0) {
                gameAverages.add(time / turns);
            }
        }

        long getAverage() {
            return totalTurns == 0 ? 0 : totalTime / totalTurns;
        }
    }
}
