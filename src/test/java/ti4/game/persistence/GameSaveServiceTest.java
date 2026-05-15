package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.testUtils.BaseTi4Test;

class GameSaveServiceTest extends BaseTi4Test {

    @Test
    void shouldSaveAndReloadGame() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setLatestOutcomeVotedFor("testOutcome");

            boolean saved = GameSaveService.save(game, "test");
            assertThat(saved).isTrue();

            Game reloaded = harness.load();
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getLatestOutcomeVotedFor()).isEqualTo("testOutcome");
        }
    }

    @Test
    void shouldSaveCreationDateTimeWithoutLegacyCreationDate() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();

            boolean saved = GameSaveService.save(game, "test");
            assertThat(saved).isTrue();

            var gameFile = Storage.getGamePath(harness.getGameName() + Constants.TXT);
            String savedMap = Files.readString(gameFile);

            assertThat(savedMap).contains(Constants.CREATION_DATE_TIME + " " + game.getCreationDateTime());
            assertThat(savedMap).doesNotContain(Constants.CREATION_DATE + " ");
        }
    }
}
