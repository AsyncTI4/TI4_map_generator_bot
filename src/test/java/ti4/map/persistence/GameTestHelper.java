package ti4.map.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import ti4.map.Game;

class GameTestHelper {
    private static final String GAME_NAME = "pbd10972";
    private static final Path SOURCE = Path.of("src/test/resources/maps", GAME_NAME + ".txt");
    private static final Path TARGET = Path.of("null/maps", GAME_NAME + ".txt");

    static Game loadGame() throws IOException {
        Files.createDirectories(TARGET.getParent());
        Files.copy(SOURCE, TARGET, StandardCopyOption.REPLACE_EXISTING);
        return GameLoadService.load(GAME_NAME);
    }
}
