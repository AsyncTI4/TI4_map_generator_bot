package ti4.game.persistence;

import java.io.File;

public record GameFileStamp(long lastModifiedEpochMilliseconds, long sizeBytes) {

    static GameFileStamp of(File gameFile) {
        return new GameFileStamp(gameFile.lastModified(), gameFile.length());
    }

    public boolean matches(GameFileStamp gameFileOnDisk) {
        return lastModifiedEpochMilliseconds > 0 && equals(gameFileOnDisk);
    }
}
