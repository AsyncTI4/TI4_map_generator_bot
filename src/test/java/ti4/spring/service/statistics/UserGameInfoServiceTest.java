package ti4.spring.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.UserEntity;

class UserGameInfoServiceTest {

    @Test
    void endedGameWithNullEndedDateIsNotCountedAsOngoing() {
        // A game that has ended (hasEnded=true) but has endedEpochMilliseconds=null
        // should NOT be counted as ongoing
        GameEntity endedGame = new GameEntity();
        endedGame.setGameName("game1");
        endedGame.setHasEnded(true);
        endedGame.setCompleted(true);
        endedGame.setEndedEpochMilliseconds(null); // No end date recorded

        UserEntity user = new UserEntity("user1", "TestUser");

        PlayerEntity player = new PlayerEntity();
        player.setUser(user);
        player.setGame(endedGame);
        player.setWinner(true);

        Map<String, UserGameInfoService.UserGameStatsAccumulator> stats =
                UserGameInfoService.buildUserStats(List.of(player));

        UserGameInfoService.UserGameStatsAccumulator userStats = stats.get("user1");
        assertThat(userStats).isNotNull();
        assertThat(userStats.ongoingGames).isZero();
        assertThat(userStats.completedGames).isEqualTo(1);
        assertThat(userStats.wins).isEqualTo(1);
        // No duration should be recorded since endedEpochMilliseconds is null
        assertThat(userStats.completedGameDays).isEmpty();
    }

    @Test
    void trulyOngoingGameIsCountedAsOngoing() {
        GameEntity ongoingGame = new GameEntity();
        ongoingGame.setGameName("game2");
        ongoingGame.setHasEnded(false);
        ongoingGame.setCompleted(false);
        ongoingGame.setEndedEpochMilliseconds(null);

        UserEntity user = new UserEntity("user1", "TestUser");

        PlayerEntity player = new PlayerEntity();
        player.setUser(user);
        player.setGame(ongoingGame);

        Map<String, UserGameInfoService.UserGameStatsAccumulator> stats =
                UserGameInfoService.buildUserStats(List.of(player));

        UserGameInfoService.UserGameStatsAccumulator userStats = stats.get("user1");
        assertThat(userStats).isNotNull();
        assertThat(userStats.ongoingGames).isEqualTo(1);
        assertThat(userStats.completedGames).isZero();
    }

    @Test
    void completedGameWithEndDateRecordsDuration() {
        long creationTime = 1000000L;
        long endedTime = creationTime + 86400000L * 5; // 5 days later

        GameEntity completedGame = new GameEntity();
        completedGame.setGameName("game3");
        completedGame.setHasEnded(true);
        completedGame.setCompleted(true);
        completedGame.setCreationEpochMilliseconds(creationTime);
        completedGame.setEndedEpochMilliseconds(endedTime);

        UserEntity user = new UserEntity("user1", "TestUser");

        PlayerEntity player = new PlayerEntity();
        player.setUser(user);
        player.setGame(completedGame);
        player.setWinner(false);

        Map<String, UserGameInfoService.UserGameStatsAccumulator> stats =
                UserGameInfoService.buildUserStats(List.of(player));

        UserGameInfoService.UserGameStatsAccumulator userStats = stats.get("user1");
        assertThat(userStats).isNotNull();
        assertThat(userStats.ongoingGames).isZero();
        assertThat(userStats.completedGames).isEqualTo(1);
        assertThat(userStats.wins).isZero();
        assertThat(userStats.completedGameDays).containsExactly(5);
    }

    @Test
    void endedButNotCompletedGameIsNeitherOngoingNorCompleted() {
        // A game that has ended without a winner (aborted)
        GameEntity abortedGame = new GameEntity();
        abortedGame.setGameName("game4");
        abortedGame.setHasEnded(true);
        abortedGame.setCompleted(false);
        abortedGame.setEndedEpochMilliseconds(System.currentTimeMillis());

        UserEntity user = new UserEntity("user1", "TestUser");

        PlayerEntity player = new PlayerEntity();
        player.setUser(user);
        player.setGame(abortedGame);

        Map<String, UserGameInfoService.UserGameStatsAccumulator> stats =
                UserGameInfoService.buildUserStats(List.of(player));

        UserGameInfoService.UserGameStatsAccumulator userStats = stats.get("user1");
        assertThat(userStats).isNotNull();
        assertThat(userStats.ongoingGames).isZero();
        assertThat(userStats.completedGames).isZero();
    }

    @Test
    void mixedGamesAreCountedCorrectly() {
        UserEntity user = new UserEntity("user1", "TestUser");

        // Truly ongoing game
        GameEntity ongoing = new GameEntity();
        ongoing.setGameName("ongoing1");
        ongoing.setHasEnded(false);
        ongoing.setCompleted(false);
        PlayerEntity p1 = new PlayerEntity();
        p1.setUser(user);
        p1.setGame(ongoing);

        // Ended game with null endedDate (the bug scenario - should NOT count as ongoing)
        GameEntity endedNoDate = new GameEntity();
        endedNoDate.setGameName("ended_no_date");
        endedNoDate.setHasEnded(true);
        endedNoDate.setCompleted(true);
        endedNoDate.setEndedEpochMilliseconds(null);
        PlayerEntity p2 = new PlayerEntity();
        p2.setUser(user);
        p2.setGame(endedNoDate);
        p2.setWinner(true);

        // Properly completed game
        GameEntity completed = new GameEntity();
        completed.setGameName("completed1");
        completed.setHasEnded(true);
        completed.setCompleted(true);
        completed.setCreationEpochMilliseconds(1000000L);
        completed.setEndedEpochMilliseconds(1000000L + 86400000L * 3);
        PlayerEntity p3 = new PlayerEntity();
        p3.setUser(user);
        p3.setGame(completed);
        p3.setWinner(false);

        Map<String, UserGameInfoService.UserGameStatsAccumulator> stats =
                UserGameInfoService.buildUserStats(List.of(p1, p2, p3));

        UserGameInfoService.UserGameStatsAccumulator userStats = stats.get("user1");
        assertThat(userStats).isNotNull();
        assertThat(userStats.ongoingGames).isEqualTo(1);
        assertThat(userStats.completedGames).isEqualTo(2);
        assertThat(userStats.wins).isEqualTo(1);
        assertThat(userStats.completedGameDays).containsExactly(3);
    }
}
