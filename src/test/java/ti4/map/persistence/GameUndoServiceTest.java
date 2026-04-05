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
import ti4.helpers.Constants;
import ti4.testUtils.BaseTi4Test;

class GameUndoServiceTest extends BaseTi4Test {

    private static final String SOURCE_GAME_NAME = "pbd15036";
    private static final String TEST_GAME_NAME = "pbd15036-undoindex";
    private static final Path SOURCE_GAME_PATH = Paths.get("src/test/resources/maps/" + SOURCE_GAME_NAME + ".txt");
    private static final Path TEST_GAME_PATH = Paths.get("src/test/resources/maps/" + TEST_GAME_NAME + ".txt");
    private static final Path UNDO_DIR = Paths.get("src/test/resources/maps/undo/" + TEST_GAME_NAME);

    @BeforeEach
    void beforeEach() throws IOException {
        Files.copy(SOURCE_GAME_PATH, TEST_GAME_PATH, StandardCopyOption.REPLACE_EXISTING);
    }

    @AfterEach
    void afterEach() throws IOException {
        Files.deleteIfExists(TEST_GAME_PATH);
        deleteDir(UNDO_DIR);
    }

    @Test
    void createUndoCopyReturnsCreatedIndex() {
        int first = GameUndoService.createUndoCopy(TEST_GAME_NAME);
        int second = GameUndoService.createUndoCopy(TEST_GAME_NAME);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(Files.exists(UNDO_DIR.resolve(TEST_GAME_NAME + "_1" + Constants.TXT)))
                .isTrue();
        assertThat(Files.exists(UNDO_DIR.resolve(TEST_GAME_NAME + "_2" + Constants.TXT)))
                .isTrue();
    }

    private static void deleteDir(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
