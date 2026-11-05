package ti4.game.persistence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.logging.BotLogger;
import ti4.settings.GlobalSettings;
import ti4.settings.GlobalSettings.ImplementedSettings;
import ti4.spring.service.persistence.PersistedManagedGameService;

@SuppressWarnings("removal")
class DatabaseWarmupRolloutTest {

    private MockedStatic<GlobalSettings> globalSettings;
    private MockedStatic<BotLogger> botLogger;
    private MockedStatic<GameDatabaseSyncPipeline> pipeline;
    private MockedStatic<GameManager> gameManager;
    private MockedStatic<PersistedManagedGameService> persistedManagedGameServiceBean;
    private PersistedManagedGameService persistedManagedGameService;

    @BeforeEach
    void setUp() {
        DatabaseWarmupRollout.reset();
        // Settings read their defaults, so reads and maintenance mode are both off unless a test says otherwise,
        // and nothing is written to the settings file.
        globalSettings = mockStatic(GlobalSettings.class, CALLS_REAL_METHODS);
        globalSettings
                .when(() -> GlobalSettings.getSetting(anyString(), eq(Boolean.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        globalSettings
                .when(() -> GlobalSettings.setSetting(any(ImplementedSettings.class), any()))
                .thenAnswer(_ -> null);
        botLogger = mockStatic(BotLogger.class);
        pipeline = mockStatic(GameDatabaseSyncPipeline.class);
        gameManager = mockStatic(GameManager.class);
        persistedManagedGameService = mock(PersistedManagedGameService.class);
        persistedManagedGameServiceBean = mockStatic(PersistedManagedGameService.class);
        persistedManagedGameServiceBean
                .when(PersistedManagedGameService::getBean)
                .thenReturn(persistedManagedGameService);
    }

    @AfterEach
    void tearDown() {
        persistedManagedGameServiceBean.close();
        gameManager.close();
        pipeline.close();
        botLogger.close();
        globalSettings.close();
        DatabaseWarmupRollout.reset();
    }

    @Test
    void doesNothingUntilAWarmupWithReadsOffHasRun() {
        DatabaseWarmupRollout.advance();

        DatabaseWarmupRollout.recordWarmupCheck(new DatabaseWarmupCheck(true));
        DatabaseWarmupRollout.advance();

        assertReadsNotTurnedOn();
        botLogger.verifyNoInteractions();
    }

    @Test
    void differingRowsAtWarmupKeepReadsOffAndAreLoggedOnce() {
        DatabaseWarmupCheck check = mock(DatabaseWarmupCheck.class);
        when(check.differingGameNames()).thenReturn(List.of("pbd1"));
        DatabaseWarmupRollout.recordWarmupCheck(check);

        DatabaseWarmupRollout.advance();
        DatabaseWarmupRollout.advance();

        assertReadsNotTurnedOn();
        botLogger.verify(() -> BotLogger.info(startsWith("Database warmup reads stay OFF: at startup 1 game")));
    }

    @Test
    void backlogProgressIsLoggedOnlyWhenItChanges() {
        DatabaseWarmupRollout.recordWarmupCheck(new DatabaseWarmupCheck(false));
        pipeline.when(GameDatabaseSyncPipeline::queuedResyncs).thenReturn(33_000);
        pipeline.when(GameDatabaseSyncPipeline::pendingResyncs).thenReturn(20_000, 20_000, 5000);

        DatabaseWarmupRollout.advance();
        DatabaseWarmupRollout.advance();
        DatabaseWarmupRollout.advance();

        assertReadsNotTurnedOn();
        botLogger.verify(() -> BotLogger.info(startsWith("Database resync backlog: 20,000 of 33,000")));
        botLogger.verify(() -> BotLogger.info(startsWith("Database resync backlog: 5,000 of 33,000")));
        // The database is only read once the backlog has drained.
        persistedManagedGameServiceBean.verifyNoInteractions();
    }

    @Test
    void outdatedRowsAfterTheBacklogAreResyncedOnceAndKeepReadsOff() {
        DatabaseWarmupRollout.recordWarmupCheck(new DatabaseWarmupCheck(false));
        List<ManagedGame> managedGames = List.of(
                managedGame("pbd1", "current"),
                managedGame("pbd2", "newer-than-row"),
                managedGame("pbd3", "no-row"),
                managedGame("pbd4", null));
        gameManager.when(GameManager::getManagedGames).thenReturn(managedGames);
        when(persistedManagedGameService.loadCurrentRevisionKeys())
                .thenReturn(Map.of("pbd1", "current", "pbd2", "older", "pbd4", "row-without-file-key"));

        DatabaseWarmupRollout.advance();
        DatabaseWarmupRollout.advance();

        assertReadsNotTurnedOn();
        pipeline.verify(() -> GameDatabaseSyncPipeline.queueSyncFromGameFile("pbd1"), never());
        pipeline.verify(() -> GameDatabaseSyncPipeline.queueSyncFromGameFile("pbd2"), times(1));
        pipeline.verify(() -> GameDatabaseSyncPipeline.queueSyncFromGameFile("pbd3"), times(1));
        pipeline.verify(() -> GameDatabaseSyncPipeline.queueSyncFromGameFile("pbd4"), times(1));
        botLogger.verify(() -> BotLogger.info(startsWith("Database warmup reads stay OFF: 3 games still have")));
    }

    @Test
    void readsAreTurnedOnOnceEveryRowMatches() {
        DatabaseWarmupRollout.recordWarmupCheck(new DatabaseWarmupCheck(false));
        List<ManagedGame> managedGames = List.of(managedGame("pbd1", "current"));
        gameManager.when(GameManager::getManagedGames).thenReturn(managedGames);
        when(persistedManagedGameService.loadCurrentRevisionKeys()).thenReturn(Map.of("pbd1", "current"));

        DatabaseWarmupRollout.advance();

        globalSettings.verify(() -> GlobalSettings.setSetting(ImplementedSettings.DATABASE_WARMUP_READS_ENABLED, true));
        botLogger.verify(() -> BotLogger.info(startsWith("Every game's database row matches its game file")));
    }

    @Test
    void maintenanceModePausesTheRollout() {
        DatabaseWarmupRollout.recordWarmupCheck(new DatabaseWarmupCheck(false));
        globalSettings
                .when(() -> GlobalSettings.getSetting(
                        eq(ImplementedSettings.SQLITE_PERSISTENCE_DISABLED.toString()), eq(Boolean.class), any()))
                .thenReturn(true);

        DatabaseWarmupRollout.advance();

        assertReadsNotTurnedOn();
        pipeline.verifyNoInteractions();
    }

    private void assertReadsNotTurnedOn() {
        globalSettings.verify(() -> GlobalSettings.setSetting(any(ImplementedSettings.class), any()), never());
    }

    private static ManagedGame managedGame(String name, String revisionKey) {
        ManagedGame managedGame = mock(ManagedGame.class);
        when(managedGame.getName()).thenReturn(name);
        when(managedGame.getRevisionKey()).thenReturn(revisionKey);
        return managedGame;
    }
}
