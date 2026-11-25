package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.persistence.GamePersistenceDriftCheck.Finding;
import ti4.game.persistence.GamePersistenceDriftCheck.Report;
import ti4.testUtils.BaseTi4Test;

class GamePersistenceDriftCheckTest extends BaseTi4Test {

    @Test
    void aGameWhoseRowAndManagedGameMatchItsFileHasNoFindings() {
        assertThat(GamePersistenceDriftCheck.compare("rev-1", "rev-1", true, "rev-1"))
                .isEmpty();
    }

    @Test
    void aMissingRowAndAMissingManagedGameAreReportedSeparatelyFromOutOfDateOnes() {
        assertThat(GamePersistenceDriftCheck.compare("rev-2", null, false, null))
                .containsExactlyInAnyOrder(Finding.DATABASE_ROW_MISSING, Finding.MANAGED_GAME_MISSING);
        assertThat(GamePersistenceDriftCheck.compare("rev-2", "rev-1", true, "rev-1"))
                .containsExactlyInAnyOrder(Finding.DATABASE_ROW_OUT_OF_DATE, Finding.MANAGED_GAME_OUT_OF_DATE);
    }

    @Test
    void aGameFileWithoutARevisionKeyCannotBeComparedSoItIsReportedOnItsOwn() {
        // The standby preload can never verify such a game, so takeover always reads it from its file.
        assertThat(GamePersistenceDriftCheck.compare(" ", "rev-1", true, "rev-1"))
                .containsExactly(Finding.NO_FILE_REVISION_KEY);
    }

    @Test
    void theSummaryCountsEachDriftedGameOnceAndLeavesOutGamesChangedDuringTheCheck() {
        Report report = new Report();
        report.add("pbd2", Set.of(Finding.DATABASE_ROW_OUT_OF_DATE, Finding.MANAGED_GAME_OUT_OF_DATE));
        report.add("pbd1", Set.of(Finding.UNREADABLE_GAME_FILE));
        report.add("pbd3", Set.of(Finding.CHANGED_DURING_CHECK));
        report.add("pbd4", Set.of());

        String summary = report.summary(4, 12);

        assertThat(summary)
                .startsWith("Checked 4 games against their database rows and ManagedGames in 12 ms: 2 off.")
                .contains("\n- Database row out of date: 1: pbd2")
                .contains("\n- Game file could not be loaded: 1 (not repaired): pbd1")
                .contains("\n- Changed during the check, so skipped: 1: pbd3")
                .contains("re-sync");
    }

    @Test
    void theSummaryListsOnlyTheFirstTenGamesOfAFindingSoItStaysWithinADiscordMessage() {
        Report report = new Report();
        for (int i = 0; i < 5000; i++) {
            for (Finding finding : Finding.values()) report.add("pbd" + i + finding.ordinal(), Set.of(finding));
        }

        String summary = report.summary(40_000, 1);

        assertThat(summary).contains(" and 4,990 more").hasSizeLessThan(2000);
    }

    @Test
    void repairingAGameRebuildsItsManagedGameAndQueuesARowSyncFromItsGameFile() {
        Game game = new Game();
        game.setName("drift-" + UUID.randomUUID());
        game.setRevisionKey("rev-new");

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class, CALLS_REAL_METHODS);
                MockedStatic<GameRevisionKeyFile> revisionKeyFile = mockStatic(GameRevisionKeyFile.class);
                MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameManager.when(() -> GameManager.peekManagedGame(anyString())).thenReturn(null);
            gameManager.when(() -> GameManager.replaceManagedGame(any())).then(_ -> null);
            revisionKeyFile.when(() -> GameRevisionKeyFile.read(game.getName())).thenReturn("rev-new");
            gameLoadService.when(() -> GameLoadService.load(game.getName())).thenReturn(game);

            Set<Finding> findings = GamePersistenceDriftCheck.checkAndRepairGame(game.getName(), "rev-old", 0);

            assertThat(findings)
                    .containsExactlyInAnyOrder(Finding.DATABASE_ROW_OUT_OF_DATE, Finding.MANAGED_GAME_MISSING);
            gameManager.verify(() -> GameManager.replaceManagedGame(any()));
            pipeline.verify(() -> GameDatabaseSyncPipeline.queueSync(eq(game), any()));
            pipeline.verifyNoMoreInteractions();
        }
    }

    @Test
    void anUnreadableGameFileIsReportedAndNeverRepaired() {
        String gameName = "drift-" + UUID.randomUUID();

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class, CALLS_REAL_METHODS);
                MockedStatic<GameRevisionKeyFile> revisionKeyFile = mockStatic(GameRevisionKeyFile.class);
                MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameManager.when(() -> GameManager.peekManagedGame(anyString())).thenReturn(null);
            revisionKeyFile.when(() -> GameRevisionKeyFile.read(gameName)).thenReturn("rev-1");
            gameLoadService.when(() -> GameLoadService.load(gameName)).thenReturn(null);

            Set<Finding> findings = GamePersistenceDriftCheck.checkAndRepairGame(gameName, null, 0);

            assertThat(findings).containsExactly(Finding.UNREADABLE_GAME_FILE);
            gameManager.verify(() -> GameManager.replaceManagedGame(any()), never());
            pipeline.verifyNoInteractions();
        }
    }

    @Test
    void aGameInSyncIsNeitherLoadedNorRepaired() {
        String gameName = "drift-" + UUID.randomUUID();

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class, CALLS_REAL_METHODS);
                MockedStatic<GameRevisionKeyFile> revisionKeyFile = mockStatic(GameRevisionKeyFile.class);
                MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class);
                MockedStatic<GameDatabaseSyncPipeline> pipeline = mockStatic(GameDatabaseSyncPipeline.class)) {
            ManagedGame managedGame = mock(ManagedGame.class);
            when(managedGame.getRevisionKey()).thenReturn("rev-1");
            gameManager.when(() -> GameManager.peekManagedGame(gameName)).thenReturn(managedGame);
            revisionKeyFile.when(() -> GameRevisionKeyFile.read(gameName)).thenReturn("rev-1");

            assertThat(GamePersistenceDriftCheck.checkAndRepairGame(gameName, "rev-1", 0))
                    .isEmpty();

            gameLoadService.verifyNoInteractions();
            pipeline.verifyNoInteractions();
        }
    }
}
