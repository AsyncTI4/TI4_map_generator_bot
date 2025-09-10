package ti4.spring.api.image;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.helpers.Storage;
import ti4.map.Game;

@Service
@RequiredArgsConstructor
public class GameImageService {

    private static final int MAX_ENTRIES = 5000;

    private final Map<String, String> lastImageByGame =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    @Nullable
    public String getLastImage(String gameName) {
        if (gameName == null || gameName.isBlank()) return null;

        String cached = lastImageByGame.get(gameName);
        if (cached != null) return cached;

        String value = readFromFile(gameName);
        if (value != null && !value.isBlank()) {
            lastImageByGame.put(gameName, value);
        }
        return value;
    }

    public void saveImage(Game game, String fileNameOnly) {
        if (game == null || fileNameOnly == null || fileNameOnly.isBlank()) return;
        String gameName = game.getName();
        writeToFile(gameName, fileNameOnly);
        lastImageByGame.put(gameName, fileNameOnly);
    }

    private static Path getStorageFilePath(String gameName) {
        String base = Storage.getStoragePath();
        if (base == null) return null;
        return Path.of(base + File.separator + "game_last_images" + File.separator + gameName + ".txt")
                .toAbsolutePath()
                .normalize();
    }

    @Nullable
    private static String readFromFile(String gameName) {
        try {
            Path path = getStorageFilePath(gameName);
            if (path == null) return null;
            if (!Files.exists(path)) return null;
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return content == null ? null : content.trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void writeToFile(String gameName, String fileNameOnly) {
        try {
            Path path = getStorageFilePath(gameName);
            if (path == null) return;
            Files.createDirectories(path.getParent());
            Files.writeString(path, fileNameOnly, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // ignore failures; cache will still hold latest within process lifetime
        }
    }
}
