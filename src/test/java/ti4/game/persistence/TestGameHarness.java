package ti4.game.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import lombok.Getter;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.Storage;

@Getter
public final class TestGameHarness implements AutoCloseable {

    private static final String DEFAULT_SOURCE_GAME_NAME = "pbd15036";
    private static final int GAME_NAME_LINE_INDEX = 2;

    private final String gameName;

    private TestGameHarness(String gameName) {
        this.gameName = gameName;
    }

    public static TestGameHarness forDefaultMap() {
        return fromSourceGame(DEFAULT_SOURCE_GAME_NAME);
    }

    public static TestGameHarness fromSourceGame(String sourceGameName) {
        String uniqueGameName = generateUniqueGameName();
        Path sourcePath = Storage.getGamePath(sourceGameName + Constants.TXT);
        Path targetPath = Storage.getGamePath(uniqueGameName + Constants.TXT);

        try {
            List<String> gameFileLines = Files.readAllLines(sourcePath);
            if (gameFileLines.size() <= GAME_NAME_LINE_INDEX) {
                throw new IllegalStateException("Test map is missing the expected game name line: " + sourcePath);
            }
            gameFileLines.set(GAME_NAME_LINE_INDEX, uniqueGameName);
            Files.write(targetPath, gameFileLines);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create test game from resource map: " + sourcePath, e);
        }

        return new TestGameHarness(uniqueGameName);
    }

    public Game load() {
        return GameLoadService.load(gameName);
    }

    @Override
    public void close() {
        deleteGameFiles(gameName);
    }

    private static void deleteGameFiles(String gameName) {
        try {
            Files.deleteIfExists(Storage.getGamePath(gameName + Constants.TXT));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to delete test game file for: " + gameName, e);
        }

        Path undoDirectory = getUndoDirectory(gameName);
        if (!Files.exists(undoDirectory)) return;

        try (Stream<Path> walk = Files.walk(undoDirectory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException("Unable to delete path: " + path, e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Unable to walk undo directory for: " + gameName, e);
        } catch (UncheckedIOException e) {
            throw new IllegalStateException("Unable to clean undo directory for: " + gameName, e.getCause());
        }
    }

    private static Path getUndoDirectory(String gameName) {
        return Storage.getBaseGameUndoDirectory()
                .resolve(gameName)
                .toAbsolutePath()
                .normalize();
    }

    private static String generateUniqueGameName() {
        String gameName;
        do {
            gameName = "pbd" + ThreadLocalRandom.current().nextLong(10_000, 10_000_000);
        } while (Files.exists(Storage.getGamePath(gameName + Constants.TXT)));
        return gameName;
    }
}
