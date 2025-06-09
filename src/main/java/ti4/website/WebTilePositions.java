package ti4.website;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Data;
import ti4.map.Game;
import ti4.map.Tile;

@Data
public class WebTilePositions {
    private List<String> tilePositions;
    public static WebTilePositions fromGame(Game game) {
        WebTilePositions webTilePositions = new WebTilePositions();

        List<String> tilePositions = new ArrayList<>();


        for (Map.Entry<String, Tile> entry : game.getTileMap().entrySet()) {
            String position = entry.getKey();
            Tile tile = entry.getValue();

            if (tile != null && tile.getTileID() != null) {
                String systemId = tile.getTileID();
                // Skip tiles that are placeholders or invalid
                if (!"-1".equals(systemId) && !"null".equals(systemId)) {
                    tilePositions.add(position + ":" + systemId);
                }
            }
        }

        tilePositions.sort(String::compareTo);
        webTilePositions.setTilePositions(tilePositions);
        return webTilePositions;
    }
}