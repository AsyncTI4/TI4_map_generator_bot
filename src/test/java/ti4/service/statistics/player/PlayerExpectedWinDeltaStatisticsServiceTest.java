package ti4.service.statistics.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.map.Game;
import ti4.map.Player;

class PlayerExpectedWinDeltaStatisticsServiceTest {

    @Test
    void calculateTracksWinsAndExpectedWinsAcrossPlayerCounts() {
        Map<String, Double> playerWinCount = new HashMap<>();
        Map<String, Double> playerExpectedWinCount = new HashMap<>();
        Map<String, Integer> playerGameCount = new HashMap<>();
        Map<String, String> playerUserIdToUsername = new HashMap<>();

        PlayerExpectedWinDeltaStatisticsService.calculate(
                buildFinishedGame(6, 1),
                playerWinCount,
                playerExpectedWinCount,
                playerGameCount,
                playerUserIdToUsername);
        PlayerExpectedWinDeltaStatisticsService.calculate(
                buildFinishedGame(4, 1),
                playerWinCount,
                playerExpectedWinCount,
                playerGameCount,
                playerUserIdToUsername);

        assertThat(playerWinCount).containsEntry("user1", 2.0);
        assertThat(playerGameCount).containsEntry("user1", 2);
        assertThat(playerExpectedWinCount.get("user1")).isCloseTo((1.0 / 6) + 0.25, within(0.000001));
        assertThat(playerExpectedWinCount.get("user2")).isCloseTo((1.0 / 6) + 0.25, within(0.000001));
        assertThat(playerGameCount).containsEntry("user5", 1);
        assertThat(playerUserIdToUsername).containsEntry("user1", "user1");
    }

    @Test
    void getPerformanceReturnsPercentOverExpectedWins() {
        assertThat(PlayerExpectedWinDeltaStatisticsService.getPerformance(2.0, (1.0 / 6) + 0.25))
                .isCloseTo(380.0, within(0.01));
        assertThat(PlayerExpectedWinDeltaStatisticsService.getPerformance(1.0, 1.0)).isZero();
        assertThat(PlayerExpectedWinDeltaStatisticsService.getPerformance(0.0, 0.5)).isEqualTo(-100.0);
    }

    private static Game buildFinishedGame(int playerCount, int winningSeat) {
        Game game = new Game();
        game.setVp(10);
        for (int i = 1; i <= playerCount; i++) {
            Player player = game.addPlayer("user" + i, "user" + i);
            player.setFaction("faction" + i);
            player.setColor("color" + i);
            player.setTotalVictoryPoints(i == winningSeat ? 10 : 0);
        }
        return game;
    }
}
