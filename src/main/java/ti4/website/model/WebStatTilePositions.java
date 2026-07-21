package ti4.website.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.helpers.PlayerStatsHelper;

@Data
public class WebStatTilePositions {
    private Map<String, List<String>> statTilePositions;

    /**
     * Drops stat-tile positions for players whose stats the viewer can't see. The tiles sit beside a
     * player's home system, so publishing them keyed by faction would give away both who is in the
     * game and roughly where they are - exactly what {@link FoWHelper#canSeeStatsOfPlayer} (which
     * bottoms out in "is their home system in view") is there to gate.
     */
    public static void redact(Map<String, List<String>> statTilePositions, Game game, Player viewer) {
        statTilePositions.keySet().removeIf(faction -> {
            Player player = game.getPlayerFromColorOrFaction(faction);
            return player != null && !FoWHelper.canSeeStatsOfPlayer(game, player, viewer);
        });
    }

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

        webStatTilePositions.statTilePositions = factionToStatTiles;
        return webStatTilePositions;
    }
}
