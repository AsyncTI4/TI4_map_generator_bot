package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.spring.service.persistence.GameEntityPersistenceService;
import ti4.spring.service.persistence.PersistedGameStateService;
import ti4.spring.service.persistence.UnreferencedUserService;
import ti4.testUtils.BaseTi4Test;

class GameRevisionKeyFileTest extends BaseTi4Test {

    @Test
    void reconciliationReadsAKeylessFileWithoutRewritingIt() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            String gameName = harness.getGameName();
            List<String> linesBefore = Files.readAllLines(gamePath(harness), StandardCharsets.UTF_8);
            assertThat(GameRevisionKeyFile.read(gameName)).isNull();

            reconcile(gameName);

            assertThat(Files.readAllLines(gamePath(harness), StandardCharsets.UTF_8))
                    .isEqualTo(linesBefore);
            assertThat(GameRevisionKeyFile.read(gameName)).isNull();
        }
    }

    @Test
    void blankKeyLineReadsAsNoKey() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            List<String> lines = new ArrayList<>(Files.readAllLines(gamePath(harness), StandardCharsets.UTF_8));
            lines.add(lines.indexOf(GamePersistenceKeys.GAMEINFO) + 1, GamePersistenceKeys.REVISION_KEY + " ");
            Files.write(gamePath(harness), lines, StandardCharsets.UTF_8);

            assertThat(GameRevisionKeyFile.read(harness.getGameName())).isNull();
        }
    }

    @Test
    void savedKeyIsReadFromTheHeader() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = GameLoadService.load(harness.getGameName());
            GameSaveService.save(game, "test");

            assertThat(GameRevisionKeyFile.read(harness.getGameName()))
                    .isNotBlank()
                    .isEqualTo(game.getRevisionKey());
        }
    }

    @Test
    void savedGameKeepsTheKeyItWasSavedWith() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = GameLoadService.load(harness.getGameName());
            GameSaveService.save(game, "test");

            reconcile(harness.getGameName());
            assertThat(GameLoadService.load(harness.getGameName()).getRevisionKey())
                    .isEqualTo(game.getRevisionKey());
        }
    }

    @Test
    void missingGameHasNoKeyAndIsNotCreated() {
        String gameName = "missing-" + UUID.randomUUID();

        Path path = Storage.getGamePath(gameName + Constants.TXT);
        assertThat(path).doesNotExist();
        assertThat(GameRevisionKeyFile.read(gameName)).isNull();
        reconcile(gameName);
        assertThat(path).doesNotExist();
    }

    private static void reconcile(String gameName) {
        Deque<Runnable> tasks = new ArrayDeque<>();
        try (MockedStatic<GameManager> manager = mockStatic(GameManager.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class);
                MockedStatic<DatabasePersistenceGate> gate = mockStatic(DatabasePersistenceGate.class);
                MockedStatic<GameEntityPersistenceService> persistence =
                        mockStatic(GameEntityPersistenceService.class);
                MockedStatic<PersistedGameStateService> states = mockStatic(PersistedGameStateService.class);
                MockedStatic<UnreferencedUserService> users = mockStatic(UnreferencedUserService.class)) {
            manager.when(GameManager::getGameNames).thenReturn(List.of(gameName));
            persistence
                    .when(GameEntityPersistenceService::getBean)
                    .thenReturn(mock(GameEntityPersistenceService.class));
            PersistedGameStateService stateService = mock(PersistedGameStateService.class);
            when(stateService.loadAll()).thenReturn(Map.of());
            states.when(PersistedGameStateService::getBean).thenReturn(stateService);
            users.when(UnreferencedUserService::getBean).thenReturn(mock(UnreferencedUserService.class));
            pipeline.when(() -> GameDatabaseSyncPipeline.queueReconciliationTask(any()))
                    .thenAnswer(invocation -> {
                        tasks.add(invocation.getArgument(0));
                        return true;
                    });
            assertThat(GameDatabaseReconciler.queueReconciliation()).isTrue();
            while (!tasks.isEmpty()) tasks.removeFirst().run();
        }
    }

    private static Path gamePath(TestGameHarness harness) {
        return Storage.getGamePath(harness.getGameName() + Constants.TXT);
    }
}
