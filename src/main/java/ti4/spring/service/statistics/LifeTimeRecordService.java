package ti4.spring.service.statistics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.persistence.PlayerEntity;
import ti4.spring.persistence.PlayerEntityRepository;

@Service
@RequiredArgsConstructor
public class LifeTimeRecordService {

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public void getLifeTimeRecords(SlashCommandInteractionEvent event) {
        try {
            tryToGetLifeTimeRecords(event);
        } catch (Exception e) {
            BotLogger.error("Error getting lifetime records", e);
        }
    }

    private void tryToGetLifeTimeRecords(SlashCommandInteractionEvent event) {
        List<User> users = getUsersFromEvent(event);
        if (users.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No players were provided.");
            return;
        }

        List<PlayerEntity> players =
                playerEntityRepository.findAllByUserIds(users.stream().map(User::getId).toList());

        Map<String, PlayerStatsAccumulator> statsByUserId = new HashMap<>();
        for (User user : users) {
            statsByUserId.put(user.getId(), new PlayerStatsAccumulator());
        }

        for (PlayerEntity player : players) {
            PlayerStatsAccumulator stats = statsByUserId.get(player.getUser().getId());
            if (stats == null) {
                continue;
            }

            stats.totalExpectedHits += player.getExpectedHits();
            stats.totalActualHits += player.getActualHits();
            stats.totalTurns += player.getTotalNumberOfTurns();
            stats.totalTurnTime += player.getTotalTurnTime();

            if (player.getGame().getEndedEpochMilliseconds() == null) {
                stats.ongoingGames++;
                continue;
            }

            if (!player.getGame().isCompleted()) {
                continue;
            }

            stats.completedGames++;
            if (player.isWinner()) {
                stats.wins++;
            }

            long gameLengthInDays = Duration.ofMillis(
                            player.getGame().getEndedEpochMilliseconds() - player.getGame().getCreationEpochMilliseconds())
                    .toDays();
            if (gameLengthInDays > 0) {
                stats.completedGameLengthsInDays.add((int) gameLengthInDays);
            }
        }

        String records = toDiceLuckString(users, statsByUserId)
                + toAverageTurnTimeString(users, statsByUserId)
                + toGamesString(users, statsByUserId);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), records);
    }

    private List<User> getUsersFromEvent(SlashCommandInteractionEvent event) {
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            User user = event.getOption(Constants.PLAYER + i, null, option -> option.getAsUser());
            if (user == null) {
                break;
            }
            users.add(user);
        }
        return users;
    }

    private String toDiceLuckString(List<User> users, Map<String, PlayerStatsAccumulator> statsByUserId) {
        StringBuilder sb = new StringBuilder("## __**Dice Luck**__\n");
        int index = 1;
        for (User user : users) {
            PlayerStatsAccumulator stats = statsByUserId.get(user.getId());
            if (stats == null || stats.totalExpectedHits == 0 || stats.totalActualHits == 0) {
                continue;
            }

            double averageDiceLuck = stats.totalActualHits / stats.totalExpectedHits;
            sb.append("`")
                    .append(Helper.leftpad(String.valueOf(index), 3))
                    .append(". ")
                    .append(String.format("%.2f", averageDiceLuck))
                    .append("` ")
                    .append(user.getEffectiveName())
                    .append("   [")
                    .append(stats.totalActualHits)
                    .append("/")
                    .append(String.format("%.1f", stats.totalExpectedHits))
                    .append(" actual/expected]\n");
            index++;

            if (stats.totalTurns == 0) {
                continue;
            }

            double expectedHitsPerTurn = stats.totalExpectedHits / stats.totalTurns;
            sb.append("`")
                    .append(Helper.leftpad(String.valueOf(index), 3))
                    .append(". ")
                    .append(String.format("%.2f", expectedHitsPerTurn))
                    .append("` ")
                    .append(user.getEffectiveName())
                    .append("   [")
                    .append(String.format("%.1f", stats.totalExpectedHits))
                    .append("/")
                    .append(stats.totalTurns)
                    .append(" expected hits/turns]\n");
            index++;
        }
        return sb.toString();
    }

    private String toAverageTurnTimeString(List<User> users, Map<String, PlayerStatsAccumulator> statsByUserId) {
        StringBuilder sb = new StringBuilder("## __**Average Turn Time:**__\n");
        int index = 1;
        for (User user : users) {
            PlayerStatsAccumulator stats = statsByUserId.get(user.getId());
            if (stats == null || stats.totalTurns == 0 || stats.totalTurnTime == 0) {
                continue;
            }

            long averageTurnTime = stats.totalTurnTime / stats.totalTurns;
            sb.append("`")
                    .append(Helper.leftpad(String.valueOf(index), 3))
                    .append(". ")
                    .append(DateTimeHelper.getTimeRepresentationToSeconds(averageTurnTime))
                    .append("` ")
                    .append(user.getEffectiveName())
                    .append("   [")
                    .append(stats.totalTurns)
                    .append(" total turns]\n");
            index++;
        }
        return sb.toString();
    }

    private String toGamesString(List<User> users, Map<String, PlayerStatsAccumulator> statsByUserId) {
        StringBuilder sb = new StringBuilder("## __**Games**__\n");
        int index = 1;
        for (User user : users) {
            PlayerStatsAccumulator stats = statsByUserId.get(user.getId());
            if (stats == null) {
                continue;
            }

            sb.append("`")
                    .append(Helper.leftpad(String.valueOf(index), 3))
                    .append(". ")
                    .append(stats.completedGames)
                    .append("` Completed. `")
                    .append(stats.ongoingGames)
                    .append("` Ongoing -- ")
                    .append(user.getEffectiveName())
                    .append("\n");

            if (stats.completedGames > 0) {
                stats.completedGameLengthsInDays.sort(Integer::compareTo);
                sb.append("> The completed games took the following amount of time to complete (in days):");
                for (Integer day : stats.completedGameLengthsInDays) {
                    sb.append(" ").append(day);
                }
                sb.append("\n");

                double winPercentage = (double) stats.wins / stats.completedGames;
                sb.append("> Player win percentage across all games was: ")
                        .append(String.format("%.2f", winPercentage))
                        .append("\n");
            }

            index++;
        }
        return sb.toString();
    }

    public static LifeTimeRecordService getBean() {
        return SpringContext.getBean(LifeTimeRecordService.class);
    }

    private static class PlayerStatsAccumulator {
        double totalExpectedHits;
        int totalActualHits;
        int totalTurns;
        long totalTurnTime;

        int completedGames;
        int ongoingGames;
        int wins;

        List<Integer> completedGameLengthsInDays = new ArrayList<>();
    }
}
