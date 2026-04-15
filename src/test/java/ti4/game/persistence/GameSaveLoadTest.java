package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class GameSaveLoadTest extends BaseTi4Test {

    @Test
    void shouldHandleSerializationOfBorderAnomalies() {
        try (var harness = TestResourceGameHarness.fromSourceGame(
                "game-with-border-anomalies", "game-with-border-anomalies-save-load")) {
            Game game = harness.load();
            String savedGameName = harness.createDerivedGameName("saved");
            game.setName(savedGameName);
            GameSaveService.save(game, "test");

            game = harness.load();
            Game game2 = GameLoadService.load(savedGameName);

            assertThat(game2)
                    .usingRecursiveComparison()
                    .ignoringFields(
                            "lastModifiedDate",
                            "draftManager.game.lastModifiedDate",
                            "name",
                            "draftManager.game.name")
                    .isEqualTo(game);

            assertThat(game.getLastModifiedDate()).isNotEqualTo(game2.getLastModifiedDate());
            assertThat(game.getName()).isNotEqualTo(game2.getName());
        }
    }

    @Test
    void shouldHandleSerializationOfDisplacedUnits() {
        try (var harness = TestResourceGameHarness.fromSourceGame(
                "game-with-displaced-units", "game-with-displaced-units-save-load")) {
            Game game = harness.load();
            String savedGameName = harness.createDerivedGameName("saved");
            game.setName(savedGameName);
            GameSaveService.save(game, "test");

            game = harness.load();
            Game game2 = GameLoadService.load(savedGameName);

            assertThat(game2)
                    .usingRecursiveComparison()
                    .ignoringFields(
                            "latestCommand",
                            "draftManager.game.latestCommand",
                            "lastModifiedDate",
                            "draftManager.game.lastModifiedDate",
                            "name",
                            "draftManager.game.name")
                    .isEqualTo(game);

            assertThat(game.getLatestCommand()).isNotEqualTo(game2.getLatestCommand());
            assertThat(game.getLastModifiedDate()).isNotEqualTo(game2.getLastModifiedDate());
            assertThat(game.getName()).isNotEqualTo(game2.getName());
        }
    }

    @Test
    void shouldHandleSaveLoadOfPlayers() {
        try (var harness = TestResourceGameHarness.fromSourceGame(
                "game-with-displaced-units", "game-with-displaced-units-player-save-load")) {
            Game game = harness.load();
            String savedGameName = harness.createDerivedGameName("saved");
            game.setName(savedGameName);
            GameSaveService.save(game, "test");

            game = harness.load();
            Game game2 = GameLoadService.load(savedGameName);

            for (Player p2 : game2.getPlayers().values()) {
                Player p = game.getPlayer(p2.getUserID());
                assertThat(p2).usingRecursiveComparison().ignoringFields("game").isEqualTo(p);
            }
            assertThat(game.getLatestCommand()).isNotEqualTo(game2.getLatestCommand());
            assertThat(game.getLastModifiedDate()).isNotEqualTo(game2.getLastModifiedDate());
            assertThat(game.getName()).isNotEqualTo(game2.getName());
        }
    }
}
