package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

class GameSnapshotsTest extends BaseTi4Test {

    private static final long SAVED_AT = 1_234_567L;

    // Rendering copies the live game. A copy is not a save, so the game must not look modified afterwards: AI caches
    // and the message save times read this stamp.
    @Test
    void copyingLeavesTheModifiedStampAlone() {
        Game game = new AiTestGame().game;
        game.newGameSetup();
        game.setLastModifiedDate(SAVED_AT);

        Game copy = GameSnapshots.copy(game);

        assertThat(game.getLastModifiedDate()).isEqualTo(SAVED_AT);
        assertThat(copy.getLastModifiedDate()).isEqualTo(SAVED_AT);
    }
}
