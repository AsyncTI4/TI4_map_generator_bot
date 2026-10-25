package ti4.image.map;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.PlayerStatsHelper;
import ti4.helpers.RenderRandom;

final class MapSideStatLayout {

    record Layout(List<Player> statOrder, Map<String, List<String>> playerStatTiles, boolean useNewSystem) {}

    private final Game game;
    private final FogView fog;
    private Layout layout;

    MapSideStatLayout(Game game, FogView fog) {
        this.game = game;
        this.fog = fog;
    }

    Layout get() {
        if (layout == null) {
            layout = compute();
        }
        return layout;
    }

    Set<String> tilePositions() {
        Layout current = get();
        Set<String> positions = new HashSet<>();
        if (current.useNewSystem()) {
            current.playerStatTiles().values().forEach(positions::addAll);
        } else {
            current.statOrder().stream()
                    .filter(Objects::nonNull)
                    .map(Player::getPlayerStatsAnchorPosition)
                    .filter(Objects::nonNull)
                    .forEach(positions::add);
        }
        return positions;
    }

    private Layout compute() {
        boolean fow = fog.isPrivate();
        List<Player> players = new ArrayList<>(game.getPlayers().values());
        List<Player> statOrder = new ArrayList<>(game.getRealPlayers());
        if (fow) {
            RenderRandom.shuffle("statLayout.fogShuffle", players);
            statOrder.clear();
            if (fog.viewer() != null) {
                statOrder.add(fog.viewer());
            }
            players.stream().filter(fog::seesHomeSystem).forEach(statOrder::add);
            players.stream().filter(fog::canSeeStats).forEach(statOrder::add);
        }

        boolean useNewSystem = true;
        Set<String> statTilesInUse = new HashSet<>();
        Map<String, List<String>> playerStatTiles = new LinkedHashMap<>();
        for (Player p : statOrder) {
            if (p == null || p.getFaction() == null || playerStatTiles.containsKey(p.getFaction())) continue;
            if (fow && !fog.canSeeStats(p)) continue;

            List<String> myStatTiles =
                    PlayerStatsHelper.findThreeNearbyStatTiles(game, p, statTilesInUse, fog.fogViewer());
            if (myStatTiles == null) {
                useNewSystem = false;
                break;
            }
            statTilesInUse.addAll(myStatTiles);
            playerStatTiles.put(p.getFaction(), myStatTiles);
        }
        return new Layout(statOrder, playerStatTiles, useNewSystem);
    }
}
