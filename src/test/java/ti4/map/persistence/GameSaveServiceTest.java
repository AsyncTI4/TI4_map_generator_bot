package ti4.map.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.map.Game;
import ti4.testUtils.BaseTi4Test;

class GameSaveServiceTest extends BaseTi4Test {

    private static final String GAME_NAME_LOAD = "pbd15036";
    private static final String GAME_NAME_COPY = "pbd15036-copy";
    private static final Path GAME_PATH = Paths.get("src/test/resources/maps/" + GAME_NAME_LOAD + ".txt");
    private static final Path COPY_PATH = Paths.get("src/test/resources/maps/" + GAME_NAME_COPY + ".txt");
    private static final Path UNDO_DIR = Paths.get("src/test/resources/maps/undo/");

    @BeforeEach
    void beforeEach() throws IOException {
        Files.copy(GAME_PATH, COPY_PATH, StandardCopyOption.REPLACE_EXISTING);
    }

    @AfterEach
    void afterEach() throws IOException {
        Files.copy(COPY_PATH, GAME_PATH, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(COPY_PATH);
        deleteDir(UNDO_DIR);
    }

    void deleteDir(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (Exception e) {
                    System.err.println("Error deleting file: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("Error deleting undo directory: " + e.getMessage());
        }
    }

    @Test
    void shouldSaveAndReloadGame() {
        Game game = GameLoadService.load(GAME_NAME_LOAD);
        game.setLatestOutcomeVotedFor("testOutcome");

        boolean saved = GameSaveService.save(game, "test");
        assertThat(saved).isTrue();

        Game reloaded = GameLoadService.load(GAME_NAME_LOAD);
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getLatestOutcomeVotedFor()).isEqualTo("testOutcome");
    }
}
