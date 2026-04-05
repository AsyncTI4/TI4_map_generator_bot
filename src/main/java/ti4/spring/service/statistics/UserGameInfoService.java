package ti4.spring.service.statistics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.helpers.Helper;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.PlayerEntityRepository;

@Service
@RequiredArgsConstructor
public class UserGameInfoService {

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public String getUserGameInfo(List<User> users) {
        List<String> userIds = users.stream().map(User::getId).toList();
        List<PlayerEntity> players = playerEntityRepository.findAllWithUsersAndGamesByUserIdIn(userIds);

        Map<String, UserGameStatsAccumulator> statsByUserId = buildUserStats(players);
        return toResultString(users, statsByUserId);
    }

    private static Map<String, UserGameStatsAccumulator> buildUserStats(List<PlayerEntity> players) {
        Map<String, UserGameStatsAccumulator> statsByUserId = new HashMap<>();
        for (PlayerEntity player : players) {
            UserGameStatsAccumulator stats = statsByUserId.computeIfAbsent(
                    player.getUser().getId(),
                    k -> new UserGameStatsAccumulator(player.getUser().getName()));

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

            long creation = player.getGame().getCreationEpochMilliseconds();
            long ended = player.getGame().getEndedEpochMilliseconds();
            int days = (int) Duration.ofMillis(ended - creation).toDays();
            stats.completedGameDays.add(days);
        }
        return statsByUserId;
    }

    private static String toResultString(List<User> users, Map<String, UserGameStatsAccumulator> statsByUserId) {
        StringBuilder sb = new StringBuilder("## __**Games**__\n");
        int index = 1;
        for (User user : users) {
            UserGameStatsAccumulator stats = statsByUserId.get(user.getId());
            if (stats == null) {
                continue;
            }

            sb.append('`')
                    .append(Helper.leftpad(String.valueOf(index), 3))
                    .append(". ")
                    .append(stats.completedGames)
                    .append("` Completed. `")
                    .append(stats.ongoingGames)
                    .append("` Ongoing -- ")
                    .append(stats.username)
                    .append('\n');

            if (stats.completedGames > 0) {
                stats.completedGameDays.sort(Comparator.naturalOrder());
                sb.append("> The completed games took the following amount of time to complete (in days):");
                for (int day : stats.completedGameDays) {
                    sb.append(" ").append(day);
                }
                sb.append('\n');

                double winPercentage = (double) stats.wins / stats.completedGames;
                sb.append("> Player win percentage across all games was: ")
                        .append(String.format("%.2f", winPercentage))
                        .append('\n');
            }
            index++;
        }
        return sb.toString();
    }

    private static class UserGameStatsAccumulator {
        private final String username;
        private int completedGames;
        private int ongoingGames;
        private int wins;
        private final List<Integer> completedGameDays = new ArrayList<>();

        UserGameStatsAccumulator(String username) {
            this.username = username;
        }
    }
}
