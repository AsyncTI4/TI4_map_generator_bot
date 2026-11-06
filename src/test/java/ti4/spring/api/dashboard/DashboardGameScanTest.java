package ti4.spring.api.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.TIGLHelper;
import ti4.testUtils.BaseTi4Test;

class DashboardGameScanTest extends BaseTi4Test {

    private static final List<String> FACTIONS = List.of("sol", "hacan", "xxcha");
    private static final List<String> COLORS = List.of("red", "blue", "green");

    @Test
    void everyDashboardValueComesFromOneLoadPerGame() {
        String userId = "dashboard-user-" + UUID.randomUUID();
        Game won = gameWithUser(userId, 3, 10);
        won.setHasEnded(true);
        won.recordWinner(won.getPlayer(userId));
        Game lost = gameWithUser(userId, 2, 20);
        lost.setHasEnded(true);
        lost.recordWinner(lost.getPlayers().values().stream()
                .filter(player -> !userId.equals(player.getUserID()))
                .findFirst()
                .orElseThrow());
        Game ongoing = gameWithUser(userId, 5, 30);
        List<ManagedGame> playerGames = List.of(loadable(ongoing), loadable(lost), loadable(won));

        DashboardService.DashboardGameScan scan = DashboardService.scanGames(userId, playerGames);

        playerGames.forEach(managedGame -> verify(managedGame, times(1)).getGame());
        assertThat(scan.wins()).isEqualTo(1);
        assertThat(scan.totalTurns()).isEqualTo(10);
        assertThat(scan.totalTurnTimeMs()).isEqualTo(Duration.ofMinutes(60).toMillis());
        // The cards keep the newest-first order the caller sorted the games into.
        assertThat(scan.games())
                .extracting(PlayerDashboardResponse.DashboardGame::gameId)
                .containsExactly(ongoing.getName(), lost.getName(), won.getName());
        assertThat(scan.games()).allSatisfy(game -> assertThat(game.yourSeat()).isNotNull());
    }

    @Test
    void rankDiceLuckAndAggregateKeyMatchThePerPassResults() {
        String userId = "dashboard-user-" + UUID.randomUUID();
        Game ongoing = gameWithUser(userId, 1, 1);
        Game older = gameWithUser(userId, 1, 1);
        older.setHasEnded(true);
        older.recordWinner(older.getPlayer(userId));
        Game oldest = gameWithUser(userId, 1, 1);
        oldest.setHasEnded(true);
        oldest.recordWinner(oldest.getPlayer(userId));
        older.getPlayer(userId).setPlayerTIGLRankAtGameStart(TIGLHelper.TIGLRank.AGENT);
        oldest.getPlayer(userId).setPlayerTIGLRankAtGameStart(TIGLHelper.TIGLRank.HERO);
        hits(ongoing.getPlayer(userId), 20, 3);
        hits(older.getPlayer(userId), 30, 4);
        ManagedGame missing = spy(new ManagedGame(gameWithUser(userId, 1, 1)));
        doReturn(null).when(missing).getGame();

        DashboardService.DashboardGameScan scan = DashboardService.scanGames(
                userId, List.of(loadable(ongoing), missing, loadable(older), loadable(oldest)));

        // Newest first, so the most recent game that recorded a rank wins.
        assertThat(scan.latestTiglRank()).isEqualTo(TIGLHelper.TIGLRank.AGENT.getName());
        assertThat(scan.diceLuckSummary().expectedHits()).isEqualTo(5.0);
        assertThat(scan.diceLuckSummary().actualHits()).isEqualTo(7.0);
        assertThat(scan.games())
                .extracting(PlayerDashboardResponse.DashboardGame::gameId)
                .containsExactly(ongoing.getName(), older.getName(), oldest.getName());
        assertThat(scan.trackedCompletedGameNames())
                .isSubsetOf(older.getName(), oldest.getName())
                .doesNotContain(ongoing.getName());
    }

    private static void hits(Player player, int expectedHitsTimes10, int actualHits) {
        player.setExpectedHitsTimes10(expectedHitsTimes10);
        player.setActualHits(actualHits);
    }

    private static ManagedGame loadable(Game game) {
        ManagedGame managedGame = spy(new ManagedGame(game));
        doReturn(game).when(managedGame).getGame();
        return managedGame;
    }

    private static Game gameWithUser(String userId, int userTurns, int userTurnMinutes) {
        Game game = new Game();
        game.setName("dashboard-scan-" + UUID.randomUUID());
        game.setLastModifiedDate(0);
        for (int index = 0; index < FACTIONS.size(); index++) {
            String seatUserId = index == 0 ? userId : "other-" + UUID.randomUUID();
            Player player = game.addPlayer(seatUserId, "Player " + index);
            player.setFaction(game, FACTIONS.get(index));
            player.setColor(COLORS.get(index));
            player.setStatsTrackedUserID(seatUserId);
            if (index == 0) {
                player.setNumberOfTurns(userTurns);
                player.setTotalTurnTime(Duration.ofMinutes(userTurnMinutes).toMillis());
            }
        }
        return game;
    }
}
