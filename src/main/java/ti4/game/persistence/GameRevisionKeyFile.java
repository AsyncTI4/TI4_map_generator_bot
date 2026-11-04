package ti4.game.persistence;

import static ti4.game.persistence.GamePersistenceKeys.ENDGAMEINFO;
import static ti4.game.persistence.GamePersistenceKeys.GAMEINFO;
import static ti4.game.persistence.GamePersistenceKeys.REVISION_KEY;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;

@UtilityClass
class GameRevisionKeyFile {

    @Nullable
    static String read(String gameName) {
        return GameFileLockManager.wrapWithReadLock(gameName, () -> readFrom(gamePath(gameName)));
    }

    // TODO: Remove once every save has a revision_key line (the first nightly reconcile after #5808 adds one to
    // each). Without it, a keyless file (e.g. restored from an undo older than #5808) is just re-read each night.
    @Deprecated(forRemoval = true)
    @Nullable
    static String backfill(String gameName) {
        return GameFileLockManager.wrapWithWriteLock(gameName, () -> {
            Path gamePath = gamePath(gameName);
            if (!Files.exists(gamePath)) return null;
            String existingRevisionKey = readFrom(gamePath);
            if (existingRevisionKey != null) return existingRevisionKey;
            String revisionKey = UUID.randomUUID().toString();
            writeRevisionKey(gamePath, revisionKey);
            return revisionKey;
        });
    }

    @Nullable
    private static String readFrom(Path gamePath) {
        try (BufferedReader reader = Files.newBufferedReader(gamePath, StandardCharsets.UTF_8)) {
            boolean inGameInfo = false;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (GAMEINFO.equals(line)) {
                    inGameInfo = true;
                } else if (ENDGAMEINFO.equals(line)) {
                    return null;
                } else if (inGameInfo && isRevisionKeyLine(line)) {
                    String revisionKey = line.substring(line.indexOf(' ') + 1).trim();
                    return revisionKey.isEmpty() ? null : revisionKey;
                }
            }
            return null;
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the revision key of " + gamePath, e);
        }
    }

    private static boolean isRevisionKeyLine(String line) {
        int separator = line.indexOf(' ');
        String key = separator < 0 ? line : line.substring(0, separator);
        return REVISION_KEY.equals(key);
    }

    private static void writeRevisionKey(Path gamePath, String revisionKey) {
        Path temporaryPath = null;
        try {
            List<String> lines = withRevisionKey(Files.readAllLines(gamePath, StandardCharsets.UTF_8), revisionKey);
            temporaryPath = Files.createTempFile(
                    gamePath.getParent(), gamePath.getFileName().toString(), ".tmp");
            Files.write(temporaryPath, lines, StandardCharsets.UTF_8);
            PosixFileSystemUtility.setPermissionsIfPosix(temporaryPath);
            Files.move(temporaryPath, gamePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write a revision key to " + gamePath, e);
        } finally {
            deleteIfPresent(temporaryPath);
        }
    }

    private static List<String> withRevisionKey(List<String> lines, String revisionKey) {
        int gameInfoIndex = lines.indexOf(GAMEINFO);
        if (gameInfoIndex < 0) {
            throw new IllegalStateException("Game file has no " + GAMEINFO + " section");
        }
        List<String> updatedLines = new ArrayList<>(lines);
        String revisionKeyLine = REVISION_KEY + " " + revisionKey;
        for (int i = gameInfoIndex + 1; i < updatedLines.size(); i++) {
            String line = updatedLines.get(i);
            if (ENDGAMEINFO.equals(line)) break;
            if (isRevisionKeyLine(line)) {
                updatedLines.set(i, revisionKeyLine);
                return updatedLines;
            }
        }
        updatedLines.add(gameInfoIndex + 1, revisionKeyLine);
        return updatedLines;
    }

    private static void deleteIfPresent(@Nullable Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            BotLogger.error("Failed to delete temporary file: " + path, e);
        }
    }

    private static Path gamePath(String gameName) {
        return Storage.getGamePath(gameName + Constants.TXT);
    }
}
