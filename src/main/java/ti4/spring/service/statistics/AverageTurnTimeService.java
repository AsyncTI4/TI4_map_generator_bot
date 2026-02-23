package ti4.spring.service.statistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.map.persistence.ManagedPlayer;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
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
        try {
            tryToGetAverageTurnTimes(event);
        } catch (Exception e) {
            BotLogger.error("Error getting average turn time", e);
        }
    }

    private void tryToGetAverageTurnTimes(SlashCommandInteractionEvent event) {
        boolean ignoreEndedGames = event.getOption(Constants.IGNORE_ENDED_GAMES, false, OptionMapping::getAsBoolean);
        boolean showMedian = event.getOption(Constants.SHOW_MEDIAN, false, OptionMapping::getAsBoolean);
        int topLimit = event.getOption(Constants.TOP_LIMIT, DEFAULT_PLAYER_LIMIT, OptionMapping::getAsInt);
        int minTurns = event.getOption(
                Constants.MINIMUM_NUMBER_OF_TURNS, DEFAULT_MINIMUM_NUMBER_OF_TURNS, OptionMapping::getAsInt);

        List<PlayerEntity> players = ignoreEndedGames
                ? playerEntityRepository.findAllPlayersOfActiveGames()
                : playerEntityRepository.findAll();

        Map<UserEntity, PlayerStatsAccumulator> statsMap = new HashMap<>();
        for (PlayerEntity player : players) {
            if (player.getTotalNumberOfTurns() == 0) {
                continue;
            }
            statsMap.computeIfAbsent(player.getUser(), user -> new PlayerStatsAccumulator(user.getName()))
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

    public static AverageTurnTimeService getBean() {
        return SpringContext.getBean(AverageTurnTimeService.class);
    }

    public void getAverageTurnTimeForGame(
            Game game,
            Map<String, Map.Entry<Integer, Long>> playerTurnTimes,
            Map<String, Set<Long>> playerAverageTurnTimes) {
        for (Player player : game.getRealPlayers()) {
            Integer totalTurns = player.getNumberOfTurns();
            Long totalTurnTime = player.getTotalTurnTime();
            Map.Entry<Integer, Long> playerTurnTime = Map.entry(totalTurns, totalTurnTime);
            String statsTrackedUserId = player.getStatsTrackedUserID();
            playerTurnTimes.merge(
                    statsTrackedUserId,
                    playerTurnTime,
                    (oldEntry, newEntry) -> Map.entry(
                            oldEntry.getKey() + playerTurnTime.getKey(),
                            oldEntry.getValue() + playerTurnTime.getValue()));

            if (playerTurnTime.getKey() == 0) {
                continue;
            }
            Long averageTurnTime = playerTurnTime.getValue() / playerTurnTime.getKey();
            playerAverageTurnTimes.compute(statsTrackedUserId, (key, value) -> {
                if (value == null) {
                    value = new HashSet<>();
                }
                value.add(averageTurnTime);
                return value;
            });
        }
    }

    public String getAverageTurnTime(List<User> users) {
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

            if (turnCount == 0 || totalMillis == 0) {
                continue;
            }

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
}
