package ti4.website.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import ti4.helpers.PlayerStatsHelper;
import ti4.map.Game;
import ti4.map.Player;

@Data
public class WebStatTilePositions {
    private Map<String, List<String>> statTilePositions;

    public static WebStatTilePositions fromGame(Game game) {
        WebStatTilePositions webStatTilePositions = new WebStatTilePositions();
        Map<String, List<String>> factionToStatTiles = new HashMap<>();

        // Logic adapted from MapGenerator.playerInfo() method
        List<Player> statOrder = game.getRealPlayers();

        Set<String> statTilesInUse = new HashSet<>();
        for (Player player : statOrder) {
            if (player == null || player.getFaction() == null || factionToStatTiles.containsKey(player.getFaction())) {
                continue;
            }

            List<String> myStatTiles =
                    PlayerStatsHelper.findThreeNearbyStatTiles(game, player, statTilesInUse, false, null);
            if (myStatTiles != null) {
                statTilesInUse.addAll(myStatTiles);
                factionToStatTiles.put(player.getFaction(), myStatTiles);
            }
        }

        webStatTilePositions.setStatTilePositions(factionToStatTiles);
        return webStatTilePositions;
    }
}
