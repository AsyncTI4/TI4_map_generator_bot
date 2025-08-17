package ti4.map.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.map.Game;
import ti4.testUtils.BaseTi4Test;

class GameSaveServiceTest extends BaseTi4Test {

    private static final Path GAME_PATH = Paths.get("src/test/resources/maps/pbd10972.txt");
    private static final Path COPIED_GAME_PATH = Paths.get("src/test/resources/maps/pbd10972-copy.txt");
    private static final Path UNDO_PATH = Paths.get("src/test/resources/maps/undo/pbd10972/pbd10972_1.txt");

    @BeforeEach
    void beforeEach() throws IOException {
        Files.copy(GAME_PATH, COPIED_GAME_PATH, StandardCopyOption.REPLACE_EXISTING);
    }

    @AfterEach
    void afterEach() throws IOException {
        Files.copy(COPIED_GAME_PATH, GAME_PATH, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(UNDO_PATH);
        Files.deleteIfExists(COPIED_GAME_PATH);
    }

    @Test
    void shouldSaveAndReloadGame() {
        Game game = GameTestHelper.loadGame();
        game.setLatestOutcomeVotedFor("testOutcome");

        boolean saved = GameSaveService.save(game, "test");
        assertThat(saved).isTrue();

        Game reloaded = GameLoadService.load("pbd10972");
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getLatestOutcomeVotedFor()).isEqualTo("testOutcome");
    }
}
