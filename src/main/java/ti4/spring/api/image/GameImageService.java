package ti4.spring.api.image;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.map.Game;
import ti4.map.persistence.GameLoadService;
import ti4.map.persistence.GameManager;

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

        Game game = GameLoadService.load(gameName);
        if (game == null) return null;

        String value = game.getLastImageFileName();
        if (value != null && !value.isBlank()) {
            lastImageByGame.put(gameName, value);
        }
        return value;
    }

    public void saveImage(Game game, String fileNameOnly) {
        if (game == null || fileNameOnly == null || fileNameOnly.isBlank()) return;
        game.setLastImageFileName(fileNameOnly);
        lastImageByGame.put(game.getName(), fileNameOnly);
        GameManager.save(game, "update last image");
    }
}
