package ti4.service.testbed;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;

@UtilityClass
public class TestBedSnapshotService {

    private static final String SNAPSHOT_FOLDER = "testbed";

    public static boolean take(Game game) {
        File gameFile = Storage.getGameFile(game.getName() + Constants.TXT);
        if (Storage.getStoragePath() == null || !gameFile.exists()) return false;
        try {
            Path snapshot = snapshotPath(game.getName());
            Files.createDirectories(snapshot.getParent());
            Files.copy(gameFile.toPath(), snapshot, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            BotLogger.error("Could not snapshot " + game.getName() + " before applying a test bed preset", e);
            return false;
        }
    }

    public static boolean exists(String gameName) {
        return Storage.getStoragePath() != null && Files.exists(snapshotPath(gameName));
    }

    @Nullable
    public static Game restore(String gameName) {
        if (!exists(gameName)) return null;
        try {
            Path gameFile = Storage.getGameFile(gameName + Constants.TXT).toPath();
            Files.copy(snapshotPath(gameName), gameFile, StandardCopyOption.REPLACE_EXISTING);
            return GameManager.reload(gameName);
        } catch (IOException e) {
            BotLogger.error("Could not restore the test bed snapshot of " + gameName, e);
            return null;
        }
    }

    private static Path snapshotPath(String gameName) {
        return Path.of(Storage.getStoragePath(), SNAPSHOT_FOLDER, gameName + Constants.TXT);
    }
}
