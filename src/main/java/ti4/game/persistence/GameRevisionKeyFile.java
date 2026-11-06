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
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.helpers.Constants;
import ti4.helpers.Storage;

@UtilityClass
class GameRevisionKeyFile {

    @Nullable
    static String read(String gameName) {
        return GameFileLockManager.wrapWithReadLock(gameName, () -> readFrom(gamePath(gameName)));
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

    private static Path gamePath(String gameName) {
        return Storage.getGamePath(gameName + Constants.TXT);
    }
}
