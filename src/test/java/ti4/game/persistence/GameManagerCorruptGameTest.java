package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.logging.BotLogger;
import ti4.service.persistence.GameDatabaseSyncPipeline;
import ti4.testUtils.BaseTi4Test;

class GameManagerCorruptGameTest extends BaseTi4Test {

    @Test
    void corruptGameIsReportedOnceUntilItLoadsAgain() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap();
                MockedStatic<BotLogger> botLogger = mockStatic(BotLogger.class)) {
            String gameName = harness.getGameName();
            harness.corruptGameFile();

            GameManager.get(gameName);
            GameManager.get(gameName);

            botLogger.verify(
                    () -> BotLogger.critical(
                            and(contains(gameName), contains("/bothelper reload_game")), any(Throwable.class)),
                    times(1));

            // Once the file loads again, a later corruption is a new problem and is reported again.
            harness.restoreGameFile();
            GameManager.get(gameName);
            harness.corruptGameFile();
            GameManager.get(gameName);

            botLogger.verify(() -> BotLogger.critical(contains(gameName), any(Throwable.class)), times(2));
        }
    }

    @Test
    void onlyAnUnparseableGameFileIsCorrupt() {
        try (var healthy = TestGameHarness.forDefaultMap();
                var corrupt = TestGameHarness.forDefaultMap()) {
            corrupt.corruptGameFile();

            assertThat(GameManager.isCorrupt(healthy.getGameName())).isFalse();
            assertThat(GameManager.isCorrupt(corrupt.getGameName())).isTrue();
            // A game whose file is gone is missing, not corrupt.
            assertThat(GameManager.isCorrupt("missing-" + UUID.randomUUID())).isFalse();
        }
    }

    @Test
    void staleCorruptResultDoesNotEvictAGameRepairedMeanwhile() {
        String userId = uniqueName("user");
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = newGame("stale-corrupt");
            game.setName(harness.getGameName());
            game.addPlayer(userId, "User");
            Game repaired = new Game();
            repaired.setName(game.getName());
            repaired.setLastModifiedDate(1);
            repaired.addPlayer(userId, "User");

            try (MockedStatic<GameSaveService> saves = mockStatic(GameSaveService.class);
                    MockedStatic<GameLoadService> loads = mockStatic(GameLoadService.class);
                    MockedStatic<GameDatabaseSyncPipeline> ignored = mockStatic(GameDatabaseSyncPipeline.class);
                    MockedStatic<BotLogger> ignoredLogs = mockStatic(BotLogger.class)) {
                saves.when(() -> GameSaveService.save(any(Game.class), anyString()))
                        .thenReturn(true);
                GameManager.save(game, "initial");
                ManagedGame examined = GameManager.getManagedGame(game.getName());

                // The file is read as corrupt, then a repair lands before that result is handled.
                loads.when(() -> GameLoadService.load(game.getName())).thenAnswer(_ -> {
                    GameManager.save(repaired, "repair");
                    return null;
                });

                assertThat(GameManager.get(game.getName())).isNull();

                ManagedGame current = GameManager.getManagedGames().stream()
                        .filter(managedGame -> managedGame.getName().equals(game.getName()))
                        .findFirst()
                        .orElseThrow();
                assertThat(current).isNotSameAs(examined);
                assertThat(GameManager.getManagedPlayer(userId).getGames())
                        .extracting(ManagedGame::getName)
                        .contains(game.getName());
            }
        }
    }

    @Test
    void bulkScanSkipsAKnownCorruptGameAndVisitsTheRest() {
        Game corrupt = newGame("bulk-corrupt");
        Game healthy = newGame("bulk-healthy");

        try (var corruptFile = TestGameHarness.forDefaultMap();
                var healthyFile = TestGameHarness.forDefaultMap();
                MockedStatic<GameSaveService> saves = mockStatic(GameSaveService.class);
                MockedStatic<GameLoadService> loads = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> ignored = mockStatic(GameDatabaseSyncPipeline.class);
                MockedStatic<BotLogger> ignoredLogs = mockStatic(BotLogger.class)) {
            saves.when(() -> GameSaveService.save(any(Game.class), anyString())).thenReturn(true);
            corrupt.setName(corruptFile.getGameName());
            healthy.setName(healthyFile.getGameName());
            GameManager.save(corrupt, "initial");
            GameManager.save(healthy, "initial");
            loads.when(() -> GameLoadService.load(corrupt.getName())).thenReturn(null);
            loads.when(() -> GameLoadService.load(healthy.getName())).thenReturn(healthy);
            // The first access finds the file corrupt and evicts the cached game, keeping its name.
            GameManager.get(corrupt.getName());
            assertThat(GameManager.isValid(corrupt.getName())).isTrue();

            List<String> visited = new ArrayList<>();
            ConsumeGameUtility.consumeGames(
                    List.of(corrupt.getName(), healthy.getName()),
                    visitedGame -> visited.add(visitedGame.getName()),
                    ExecutionLockType.READ);

            assertThat(visited).containsExactly(healthy.getName());
        }
    }

    private static Game newGame(String prefix) {
        Game game = new Game();
        game.setName(uniqueName(prefix));
        game.setLastModifiedDate(0);
        return game;
    }

    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
