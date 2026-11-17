package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.testUtils.BaseTi4Test;

class ManagedGameWarmupTest extends BaseTi4Test {

    @Test
    void aStandbyBuiltGameThatIsStillCurrentIsKept() {
        String userId = uniqueName("kept-user");
        ManagedGame standbyBuilt = saveAndGetManagedGame(newGame("prebuilt-kept", userId));

        int kept = ManagedGameWarmup.discardOutdatedStandbyBuilds(
                Map.of(standbyBuilt.getName(), standbyBuilt), Set.of(standbyBuilt.getName()));

        assertThat(kept).isEqualTo(1);
        assertThat(GameManager.peekManagedGame(standbyBuilt.getName())).isSameAs(standbyBuilt);
        assertThat(gameNamesOf(userId)).contains(standbyBuilt.getName());
    }

    @Test
    void aStandbyBuiltGameSavedSinceThePreloadIsDiscardedAndLeavesItsPlayers() {
        String userId = uniqueName("discarded-user");
        ManagedGame standbyBuilt = saveAndGetManagedGame(newGame("prebuilt-discarded", userId));

        // An empty still-current set means the game file was saved after the standby preload.
        int kept =
                ManagedGameWarmup.discardOutdatedStandbyBuilds(Map.of(standbyBuilt.getName(), standbyBuilt), Set.of());

        assertThat(kept).isZero();
        assertThat(GameManager.peekManagedGame(standbyBuilt.getName())).isNull();
        assertThat(gameNamesOf(userId)).doesNotContain(standbyBuilt.getName());
    }

    @Test
    void aGameReplacedByASaveDuringTakeoverIsNeverDiscarded() {
        String userId = uniqueName("resaved-user");
        Game game = newGame("prebuilt-resaved", userId);
        ManagedGame standbyBuilt = saveAndGetManagedGame(game);
        game.setLastModifiedDate(1);
        ManagedGame savedDuringTakeover = saveAndGetManagedGame(game);

        ManagedGameWarmup.discardOutdatedStandbyBuilds(Map.of(game.getName(), standbyBuilt), Set.of());

        // The save already replaced the standby-built instance, so the newer one and its player link survive.
        assertThat(GameManager.peekManagedGame(game.getName())).isSameAs(savedDuringTakeover);
        // ManagedGame equality is by name, so check the instance the player holds.
        assertThat(GameManager.getManagedPlayer(userId).getGames())
                .singleElement()
                .isSameAs(savedDuringTakeover);
    }

    @Test
    void removingAGameFromAPlayerLeavesANewerInstanceOfThatGameInPlace() {
        String userId = uniqueName("instance-user");
        Game game = newGame("player-instance", userId);
        ManagedGame older = saveAndGetManagedGame(game);
        game.setLastModifiedDate(1);
        ManagedGame newer = saveAndGetManagedGame(game);
        ManagedPlayer player = GameManager.getManagedPlayer(userId);

        player.removeGame(game.getName(), older);

        assertThat(player.getGames()).singleElement().isSameAs(newer);
    }

    @Test
    void aGameSavedSinceThePreloadIsNotCurrentEvenWhileItsDatabaseRowStillLags() {
        // The old instance releases the lease before its database sync drains, so a game saved just before the
        // drain can still show its preload revision in the database at takeover.
        ManagedGamePreload.Snapshot preload = new ManagedGamePreload.Snapshot(
                Map.of("saved-late", preloadedState("saved-late"), "untouched", preloadedState("untouched")),
                Set.of("saved-late", "untouched"),
                0L);
        // Only the game file's modified time decides: the lagging row is never consulted.
        assertThat(ManagedGameWarmup.stillCurrent(preload, Set.of("saved-late"), Set.of("saved-late", "untouched")))
                .containsExactly("untouched");
    }

    @Test
    void oneScanOfTheGameFolderListsTheSameGamesAsTheNameListing() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            GameLoadService.GameFolder gameFolder = GameLoadService.scanGameFolder();

            assertThat(gameFolder.gameNames()).containsExactlyElementsOf(GameLoadService.loadGameNames());
            assertThat(gameFolder.lastModifiedMillis()).containsKey(harness.getGameName());
        }
    }

    @Test
    void gameFilesSavedSinceThePreloadAreFoundByTheirModifiedTime() throws Exception {
        try (var harness = TestGameHarness.forDefaultMap()) {
            String gameName = harness.getGameName();
            Path gameFile = Storage.getGamePath(gameName + Constants.TXT);
            long preloadStartedAt = System.currentTimeMillis();
            Files.setLastModifiedTime(gameFile, FileTime.fromMillis(preloadStartedAt - 60_000));

            assertThat(GameLoadService.scanGameFolder().savedSince(preloadStartedAt))
                    .doesNotContain(gameName);

            Files.setLastModifiedTime(gameFile, FileTime.fromMillis(preloadStartedAt + 1000));

            assertThat(GameLoadService.scanGameFolder().savedSince(preloadStartedAt))
                    .contains(gameName);
        }
    }

    private static ManagedGameState preloadedState(String gameName) {
        return new ManagedGameState(
                gameName, "rev-1", false, false, false, false, false, false, false, false, false, false, false, false,
                true, false, 0, 0, null, 0, 0, 1, null, null, null, null, List.of(), Map.of());
    }

    private static ManagedGame saveAndGetManagedGame(Game game) {
        try (MockedStatic<GameSaveService> gameSaveService = mockStatic(GameSaveService.class);
                MockedStatic<GameDatabaseSyncPipeline> ignored = mockStatic(GameDatabaseSyncPipeline.class)) {
            gameSaveService
                    .when(() -> GameSaveService.save(any(Game.class), anyString()))
                    .thenReturn(true);
            GameManager.save(game, "test save");
        }
        return GameManager.peekManagedGame(game.getName());
    }

    private static Game newGame(String prefix, String userId) {
        Game game = new Game();
        game.setName(uniqueName(prefix));
        game.setLastModifiedDate(0);
        game.addPlayer(userId, "Test User");
        return game;
    }

    private static List<String> gameNamesOf(String userId) {
        return GameManager.getManagedPlayer(userId).getGames().stream()
                .map(ManagedGame::getName)
                .toList();
    }

    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
