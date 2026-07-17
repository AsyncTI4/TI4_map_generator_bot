package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class GameSaveLoadTest extends BaseTi4Test {

    @Test
    void shouldHandleSerializationOfBorderAnomalies() {
        try (var harness = TestGameHarness.fromSourceGame("game-with-border-anomalies")) {
            Game game = harness.load();

            Game game2 = harness.load();
            GameSaveService.save(game2, "testing");
            game2 = harness.load();

            assertThat(game2)
                    .usingRecursiveComparison()
                    .ignoringFields(
                            "latestCommand",
                            "draftManager.game.latestCommand",
                            "lastModifiedDate",
                            "draftManager.game.lastModifiedDate")
                    .isEqualTo(game);

            assertThat(game.getLatestCommand()).isNotEqualTo(game2.getLatestCommand());
            assertThat(game.getLastModifiedDate()).isNotEqualTo(game2.getLastModifiedDate());
        }
    }

    @Test
    void shouldHandleSerializationOfDisplacedUnits() {
        try (var harness = TestGameHarness.fromSourceGame("game-with-displaced-units")) {
            Game game = harness.load();

            Game game2 = harness.load();
            GameSaveService.save(game2, "test");
            game2 = harness.load();

            assertThat(game2)
                    .usingRecursiveComparison()
                    .ignoringFields(
                            "latestCommand",
                            "draftManager.game.latestCommand",
                            "lastModifiedDate",
                            "draftManager.game.lastModifiedDate")
                    .isEqualTo(game);

            assertThat(game.getLatestCommand()).isNotEqualTo(game2.getLatestCommand());
            assertThat(game.getLastModifiedDate()).isNotEqualTo(game2.getLastModifiedDate());
        }
    }

    @Test
    void shouldHandleSaveLoadOfPlayers() {
        try (var harness = TestGameHarness.fromSourceGame("game-with-displaced-units")) {
            Game game = harness.load();

            Game game2 = harness.load();
            GameSaveService.save(game2, "test");
            game2 = harness.load();

            game2.getPlayers().values().forEach(p2 -> {
                Player p = game.getPlayer(p2.getUserID());
                assertThat(p2).usingRecursiveComparison().ignoringFields("game").isEqualTo(p);
            });

            assertThat(game.getLatestCommand()).isNotEqualTo(game2.getLatestCommand());
            assertThat(game.getLastModifiedDate()).isNotEqualTo(game2.getLastModifiedDate());
        }
    }
}
