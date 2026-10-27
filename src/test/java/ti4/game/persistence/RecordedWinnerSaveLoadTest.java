package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class RecordedWinnerSaveLoadTest extends BaseTi4Test {

    @Test
    void recordedWinnerSurvivesSaveAndLoad() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            // Raise the goal out of reach so only the recorded winner can produce a winner.
            game.setVp(99);
            Player recorded = game.getRealPlayers().getFirst();
            game.recordWinner(recorded);
            game.setHasEnded(true);
            GameSaveService.save(game, "test");

            Game reloaded = harness.load();

            assertThat(reloaded.getWinner()).map(Player::getFaction).contains(recorded.getFaction());
        }
    }
}
