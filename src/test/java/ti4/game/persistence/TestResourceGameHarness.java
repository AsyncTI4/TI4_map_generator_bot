package ti4.game.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.Storage;

final class TestResourceGameHarness implements AutoCloseable {
    private static final String DEFAULT_SOURCE_GAME_NAME = "pbd15036";
    private static final int GAME_NAME_LINE_INDEX = 2;

    private final String gameName;
    private final Set<String> managedGameNames = new LinkedHashSet<>();

    private TestResourceGameHarness(String gameName) {
        this.gameName = gameName;
        managedGameNames.add(gameName);
    }

    static TestResourceGameHarness forDefaultMap(String testName) {
        return fromSourceGame(DEFAULT_SOURCE_GAME_NAME, testName);
    }

    static TestResourceGameHarness fromSourceGame(String sourceGameName, String testName) {
        String uniqueGameName = sanitize(testName) + "-" + UUID.randomUUID();
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

        return new TestResourceGameHarness(uniqueGameName);
    }

    String getGameName() {
        return gameName;
    }

    String createDerivedGameName(String suffix) {
        String derivedGameName = gameName + "-" + sanitize(suffix);
        managedGameNames.add(derivedGameName);
        return derivedGameName;
    }

    Game load() {
        return GameLoadService.load(gameName);
    }

    Path buildUndoPath(int undoIndex) {
        return Storage.getGameUndo(gameName, gameName + "_" + undoIndex + Constants.TXT);
    }

    @Override
    public void close() {
        managedGameNames.forEach(TestResourceGameHarness::deleteGameFiles);
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

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9-]+", "-");
    }
}
