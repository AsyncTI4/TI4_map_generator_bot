package ti4.helpers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

public final class Storage {

    public static final String ENV_VAR_RESOURCE_PATH = "RESOURCE_PATH";
    private static final String ENV_VAR_DB_PATH = "DB_PATH";
    private static final String GAMES_UNDO = "/maps/undo/";
    private static final String GAMES_PATH = "/maps/";
    private static final String DELETED_GAMES_PATH = "/deletedmaps/";
    private static final String TTPG_EXPORTS = "/ttpg_exports/";
    private static final String CHANNEL_EXPORTS = "/exported_channels/";

    private static String resourcePath;

    @NotNull
    public static File getAppEmojiDirectory() {
        return new File(getResourcePath() + "/emojis/");
    }

    @NotNull
    @SneakyThrows
    public static Path getGameUndo(String gameName, String fileName) {
        var path = Path.of(getStoragePath() + GAMES_UNDO + gameName + File.separator + fileName);
        Files.createDirectories(path.getParent());
        return path;
    }

    @NotNull
    @SneakyThrows
    public static Path getGameUndoDirectory(String gameName) {
        Path directory = Path.of(getStoragePath() + GAMES_UNDO + gameName);
        Files.createDirectories(directory);
        return directory;
    }

    @NotNull
    @SneakyThrows
    public static Path getBaseGameUndoDirectory() {
        Path directory = Path.of(getStoragePath() + GAMES_UNDO);
        Files.createDirectories(directory);
        return directory;
    }

    @NotNull
    public static File getGameFile(String gameName) {
        return new File(getStoragePath() + GAMES_PATH + gameName);
    }

    @NotNull
    public static Path getGamePath(String gameName) {
        return Path.of(getStoragePath() + GAMES_PATH + gameName)
                .toAbsolutePath()
                .normalize();
    }

    @NotNull
    public static File getGamesDirectory() {
        var file = new File(getStoragePath() + GAMES_PATH);
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    @NotNull
    public static File getDeletedGame(String gameName) {
        return new File(getStoragePath() + DELETED_GAMES_PATH + gameName);
    }

    @NotNull
    public static File getTTPGExportDirectory() {
        return new File(getStoragePath() + TTPG_EXPORTS);
    }

    @NotNull
    public static File getTTPGExportStorage(String fileName) {
        return new File(getStoragePath() + TTPG_EXPORTS + fileName);
    }

    @NotNull
    public static Path getChannelExportsDirectory() {
        return Path.of(getStoragePath() + CHANNEL_EXPORTS);
    }

    public static void init() {
        String resource = getStoragePath();
        if (resource != null) {
            createDirectory(DELETED_GAMES_PATH);
            createDirectory(GAMES_PATH);
            createDirectory(TTPG_EXPORTS);
        }
    }

    private static void createDirectory(String directoryName) {
        File directory = new File(getStoragePath() + directoryName);
        if (!directory.exists()) {
            directory.mkdir();
        }
    }

    @Nullable
    public static File getLoggerFile() {
        String resource = getStoragePath();
        if (resource == null) return null;
        return new File(getStoragePath() + "/log/log.txt");
    }

    public static String getStoragePath() {
        return System.getenv(ENV_VAR_DB_PATH);
    }

    @Nullable
    public static String getResourcePath() {
        if (resourcePath != null) {
            return resourcePath;
        }

        return System.getenv(ENV_VAR_RESOURCE_PATH);
    }

    /**
     * Allows for resource path overrides instead of using env var. Likely only useful
     * for testing.
     */
    public static void setResourcePath(@Nullable String path) {
        resourcePath = path;
    }
}
