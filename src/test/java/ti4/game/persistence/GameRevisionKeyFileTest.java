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
    void backfillWritesOneKeyThatLoadingAndLaterReadsSee() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            String gameName = harness.getGameName();
            List<String> linesBefore = Files.readAllLines(gamePath(harness), StandardCharsets.UTF_8);
            assertThat(GameLoadService.load(gameName).getRevisionKey()).isNull();

            reconcile(gameName);
            String revisionKey = GameLoadService.load(gameName).getRevisionKey();

            assertThat(revisionKey).isNotBlank();
            assertThat(GameLoadService.load(gameName).getRevisionKey()).isEqualTo(revisionKey);
            reconcile(gameName);
            assertThat(GameLoadService.load(gameName).getRevisionKey()).isEqualTo(revisionKey);
            Game game = GameLoadService.load(gameName);
            assertThat(game).isNotNull();
            assertThat(game.getRevisionKey()).isEqualTo(revisionKey);

            // Only the key line is added; every other line of the save is untouched.
            List<String> linesAfter = Files.readAllLines(gamePath(harness), StandardCharsets.UTF_8);
            List<String> withoutKey = new ArrayList<>(linesAfter);
            withoutKey.remove(GamePersistenceKeys.REVISION_KEY + " " + revisionKey);
            assertThat(withoutKey).isEqualTo(linesBefore);
        }
    }

    @Test
    void blankKeyLineIsReplacedRatherThanDuplicated() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            List<String> lines = new ArrayList<>(Files.readAllLines(gamePath(harness), StandardCharsets.UTF_8));
            lines.add(lines.indexOf(GamePersistenceKeys.GAMEINFO) + 1, GamePersistenceKeys.REVISION_KEY + " ");
            Files.write(gamePath(harness), lines, StandardCharsets.UTF_8);
            assertThat(GameLoadService.load(harness.getGameName()).getRevisionKey())
                    .isNullOrEmpty();

            reconcile(harness.getGameName());
            String revisionKey = GameLoadService.load(harness.getGameName()).getRevisionKey();

            List<String> keyLines = Files.readAllLines(gamePath(harness), StandardCharsets.UTF_8).stream()
                    .filter(line -> line.startsWith(GamePersistenceKeys.REVISION_KEY))
                    .toList();
            assertThat(keyLines).containsExactly(GamePersistenceKeys.REVISION_KEY + " " + revisionKey);
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
