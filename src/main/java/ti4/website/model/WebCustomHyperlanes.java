package ti4.website.model;

import java.util.Map;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.service.map.CustomHyperlaneService;

@UtilityClass
public class WebCustomHyperlanes {

    public static Map<String, String> fromGame(Game game) {
        Map<String, String> matricesByPosition = new TreeMap<>();
        for (Map.Entry<String, String> entry : game.getCustomHyperlaneData().entrySet()) {
            Tile tile = game.getTileByPosition(entry.getKey());
            if (tile == null || !CustomHyperlaneService.isCustomHyperlaneTile(tile)) continue;
            String matrix = entry.getValue();
            if (matrix == null || !CustomHyperlaneService.isValidStoredMatrix(matrix)) continue;
            matricesByPosition.put(entry.getKey(), matrix);
        }
        return matricesByPosition;
    }
}
