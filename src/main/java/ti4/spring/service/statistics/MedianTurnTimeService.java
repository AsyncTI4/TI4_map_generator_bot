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
public class MedianTurnTimeService {

    private static final int DEFAULT_PLAYER_LIMIT = 50;
    private static final int DEFAULT_MINIMUM_NUMBER_OF_TURNS = 100;

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public void getMedianTurnTimes(SlashCommandInteractionEvent event) {
        boolean ignoreEndedGames = event.getOption(Constants.IGNORE_ENDED_GAMES, false, OptionMapping::getAsBoolean);
        int topLimit = event.getOption(Constants.TOP_LIMIT, DEFAULT_PLAYER_LIMIT, OptionMapping::getAsInt);
        int minimumTurns = event.getOption(
                Constants.MINIMUM_NUMBER_OF_TURNS, DEFAULT_MINIMUM_NUMBER_OF_TURNS, OptionMapping::getAsInt);

        List<PlayerEntity> players = ignoreEndedGames
                ? playerEntityRepository.findAllWithUsersByActiveGame()
                : playerEntityRepository.findAllWithUsers();

        Map<UserEntity, PlayerStatsAccumulator> statsMap = new HashMap<>();
        for (PlayerEntity player : players) {
            if (player.getTotalNumberOfTurns() == 0) continue;
            statsMap.computeIfAbsent(player.getUser(), user -> new PlayerStatsAccumulator(user.getName()))
                    .addGame(player.getTotalNumberOfTurns(), player.getTotalTurnTime());
        }

        List<PlayerStatsAccumulator> sortedResults = statsMap.values().stream()
                .filter(s -> s.totalTurns >= minimumTurns)
                .sorted(Comparator.comparingLong(PlayerStatsAccumulator::getMedian))
                .limit(topLimit)
                .toList();

        String result = toResultString(sortedResults);

        MessageHelper.sendMessageToThread(event.getChannel(), "Median Turn Time", result);
    }

    private String toResultString(List<PlayerStatsAccumulator> sortedResults) {
        StringBuilder sb = new StringBuilder("## __**Median Turn Time:**__\n");
        int index = 1;
        for (var stats : sortedResults) {
            sb.append("`").append(Helper.leftpad(String.valueOf(index), 3)).append(". ");
            index++;
            sb.append(DateTimeHelper.getTimeRepresentationToSeconds(stats.getMedian()));
            sb.append("` ")
                    .append(stats.username)
                    .append("   [")
                    .append(stats.totalTurns)
                    .append(" total turns]\n");
        }
        return sb.toString();
    }

    public static MedianTurnTimeService getBean() {
        return SpringContext.getBean(MedianTurnTimeService.class);
    }

    private static class PlayerStatsAccumulator {
        private final String username;
        private int totalTurns;
        private long median = -1;
        private final List<Long> gameAverages = new ArrayList<>();

        PlayerStatsAccumulator(String username) {
            this.username = username;
        }

        void addGame(int turns, long time) {
            totalTurns += turns;
            gameAverages.add(time / turns);
        }

        long getMedian() {
            if (totalTurns == 0) return 0;
            if (median == -1) {
                median = (long) Helper.median(gameAverages);
            }
            return median;
        }
    }
}
