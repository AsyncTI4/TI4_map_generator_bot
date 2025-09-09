package ti4.spring.api.image;

import static software.amazon.awssdk.utils.StringUtils.isBlank;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;

@Service
@RequiredArgsConstructor
public class GameImageService {

    private static final Cache<String, String> gameNameToLastImageFileNameCache =
            Caffeine.newBuilder().maximumSize(5000).build();

    @Nullable
    String getLastImage(String gameName) {
        if (!GameManager.isValid(gameName)) return null;

        ManagedGame managedGame = GameManager.getManagedGame(gameName);

        return gameNameToLastImageFileNameCache.get(
                gameName, k -> managedGame.getGame().getLastImageFileName());
    }

    public void saveImage(Game game, String lastImageFileName) {
        if (game == null || isBlank(lastImageFileName)) return;

        game.setLastImageFileName(lastImageFileName);
        gameNameToLastImageFileNameCache.put(game.getName(), lastImageFileName);
        GameManager.save(game, "update last image");
    }
}
