package ti4.spring.service.statistics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ManagedGame;
import ti4.game.persistence.ManagedPlayer;
import ti4.helpers.Helper;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.PlayerEntityRepository;

@Service
@RequiredArgsConstructor
public class UserGameInfoService {

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public List<Integer> getUsersThreeFastestDaysToComplete6PlayerGames(String userId) {
        return playerEntityRepository.findAllWithGamesByUserIdEquals(userId).stream()
                .map(PlayerEntity::getGame)
                .filter(game -> game.getPlayerCount() == 6)
                .map(UserGameInfoService::getDaysToComplete)
                .filter(days -> days > 0)
                .sorted()
                .limit(5)
                .toList();
    }

    private static int getDaysToComplete(GameEntity game) {
        return (int) Duration.ofMillis(game.getEndedEpochMilliseconds() - game.getCreationEpochMilliseconds())
                .toDays();
    }

    @Transactional(readOnly = true)
    public boolean hasCompletedGameInDays(String userId, int maxDurationDays) {
        return playerEntityRepository.findAllWithCompletedGamesByUserIdEquals(userId).stream()
                .map(PlayerEntity::getGame)
                .map(UserGameInfoService::getDaysToComplete)
                .anyMatch(days -> days <= maxDurationDays);
    }

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
                    _ -> new UserGameStatsAccumulator(player.getUser().getName()));

            if (player.getGame().getEndedEpochMilliseconds() == null) {
                if (player.isReplaced()) {
                    stats.replacedOngoing++;
                } else {
                    stats.ongoingGames++;
                }
                continue;
            }

            if (!player.getGame().isCompleted()) {
                continue;
            }

            if (player.isReplaced()) {
                stats.replacedCompleted++;
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
                    .append("` Completed");
            if (stats.replacedCompleted > 0) {
                sb.append(" (replaced in ")
                        .append(stats.replacedCompleted)
                        .append(stats.replacedCompleted == 1 ? " other)" : " others)");
            }
            sb.append(". `").append(stats.ongoingGames).append("` Ongoing");
            if (stats.replacedOngoing > 0) {
                sb.append(" (replaced in ")
                        .append(stats.replacedOngoing)
                        .append(stats.replacedOngoing == 1 ? " other)" : " others)");
            }
            sb.append(" -- ").append(stats.username).append('\n');

            if (stats.completedGames > 0) {
                stats.completedGameDays.sort(Comparator.naturalOrder());
                sb.append("> The completed games took the following amount of time to complete (in days):");
                for (int day : stats.completedGameDays) {
                    sb.append(' ').append(day);
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

    public static int countOngoingGamesThatAffectJoinLimit(ManagedPlayer managedPlayer) {
        if (managedPlayer == null) return 0;
        return (int) managedPlayer.getGames().stream()
                .filter(managedGame -> !managedGame.isHasEnded())
                .map(ManagedGame::getGame)
                .filter(isRealPlayerIn3PlusPlayerGame(managedPlayer))
                .count();
    }

    public static int countCompletedGamesThatAffectJoinLimit(ManagedPlayer managedPlayer) {
        if (managedPlayer == null) return 0;
        return (int) managedPlayer.getGames().stream()
                .filter(managedGame -> managedGame.isHasEnded() && managedGame.isHasWinner())
                .map(ManagedGame::getGame)
                .filter(isRealPlayerIn3PlusPlayerGame(managedPlayer))
                .count();
    }

    public static boolean isOverStandardGameLimit(ManagedPlayer managedPlayer) {
        if (managedPlayer == null) return false;
        int ongoingAmount = countOngoingGamesThatAffectJoinLimit(managedPlayer);
        int completedGames = countCompletedGamesThatAffectJoinLimit(managedPlayer);
        return ongoingAmount > completedGames + 2;
    }

    private static Predicate<Game> isRealPlayerIn3PlusPlayerGame(ManagedPlayer managedPlayer) {
        return game -> {
            List<Player> realAndEliminatedPlayers = game.getRealAndEliminatedPlayers();
            return realAndEliminatedPlayers.size() >= 3
                    && realAndEliminatedPlayers.stream()
                            .map(Player::getUserID)
                            .anyMatch(id -> managedPlayer.getId().equals(id));
        };
    }

    public static UserGameInfoService get() {
        return SpringContext.getBean(UserGameInfoService.class);
    }

    private static class UserGameStatsAccumulator {
        private final String username;
        private int completedGames;
        private int ongoingGames;
        private int replacedCompleted;
        private int replacedOngoing;
        private int wins;
        private final List<Integer> completedGameDays = new ArrayList<>();

        UserGameStatsAccumulator(String username) {
            this.username = username;
        }
    }
}
