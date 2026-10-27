package ti4.service.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ManagedGame;
import ti4.spring.service.persistence.GameEntityMapper;
import ti4.spring.service.persistence.PersistedGameState;
import ti4.testUtils.BaseTi4Test;

class GameDatabaseReconcilerSkipTest extends BaseTi4Test {

    private static final List<String> FACTIONS = List.of("sol", "hacan", "xxcha");
    private static final List<String> COLORS = List.of("red", "blue", "green");

    @Test
    void gameSyncedFromItsCurrentSaveIsSkipped() {
        Game game = gameWithPlayers(3, 1_000L);
        PersistedGameState persisted = PersistedGameState.of(GameEntityMapper.toSnapshot(game));

        assertThat(GameDatabaseReconciler.isUnchangedSinceLastSync(new ManagedGame(game), persisted))
                .isTrue();
    }

    @Test
    void gameSavedAfterItsLastSyncIsCompared() {
        Game game = gameWithPlayers(3, 1_000L);
        PersistedGameState persisted = PersistedGameState.of(GameEntityMapper.toSnapshot(game));
        game.setLastModifiedDate(2_000L);

        assertThat(GameDatabaseReconciler.isUnchangedSinceLastSync(new ManagedGame(game), persisted))
                .isFalse();
    }

    @Test
    void rowWrittenBeforeTheTimestampColumnExistedIsCompared() {
        Game game = gameWithPlayers(3, 1_000L);
        var snapshot = GameEntityMapper.toSnapshot(game);
        snapshot.game().setSourceLastModifiedEpochMilliseconds(null);

        assertThat(GameDatabaseReconciler.isUnchangedSinceLastSync(
                        new ManagedGame(game), PersistedGameState.of(snapshot)))
                .isFalse();
    }

    @Test
    void gameWithoutRowIsSkippedOnlyWhenTooSmallToPersist() {
        assertThat(GameDatabaseReconciler.isUnchangedSinceLastSync(new ManagedGame(gameWithPlayers(2, 1_000L)), null))
                .isTrue();
        assertThat(GameDatabaseReconciler.isUnchangedSinceLastSync(new ManagedGame(gameWithPlayers(3, 1_000L)), null))
                .isFalse();
    }

    private static Game gameWithPlayers(int playerCount, long lastModified) {
        Game game = new Game();
        game.setName("reconciler-skip-" + UUID.randomUUID());
        game.setLastModifiedDate(lastModified);
        for (int index = 0; index < playerCount; index++) {
            Player player = game.addPlayer("reconciler-user-" + UUID.randomUUID(), "Player " + index);
            player.setFaction(game, FACTIONS.get(index));
            player.setColor(COLORS.get(index));
        }
        return game;
    }
}
