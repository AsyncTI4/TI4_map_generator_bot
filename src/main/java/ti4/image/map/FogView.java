package ti4.image.map;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.fow.FogViewer;

final class FogView {

    private final FogViewer viewer;
    private final Set<String> visibleSystems;
    private final Map<String, Tile> tiles;

    private FogView(FogViewer viewer, Set<String> visibleSystems, Map<String, Tile> tiles) {
        this.viewer = viewer;
        this.visibleSystems = visibleSystems;
        this.tiles = tiles;
    }

    static FogView of(Game game, @Nullable GenericInteractionCreateEvent event) {
        return of(game, FogViewer.resolve(game, event, null));
    }

    static FogView of(Game game, FogViewer viewer) {
        if (!viewer.privateView()) {
            return new FogView(viewer, Set.of(), new HashMap<>(game.getTileMap()));
        }
        Player player = viewer.player();
        Set<String> visibleSystems = viewer.visibleSystems();
        Map<String, Tile> tiles = new HashMap<>(game.getTileMap());
        Set<String> hiddenPositions = new HashSet<>(tiles.keySet());
        hiddenPositions.removeAll(visibleSystems);
        for (String position : hiddenPositions) {
            tiles.remove(position);
            if (player != null) {
                Tile fogTile = player.buildFogTile(position, player);
                if (fogTile != null) {
                    tiles.put(position, fogTile);
                }
            }
        }
        return new FogView(viewer, visibleSystems, tiles);
    }

    static FogView unfogged(Game game) {
        return new FogView(FogViewer.unfogged(game), Set.of(), new HashMap<>(game.getTileMap()));
    }

    boolean isPrivate() {
        return viewer.privateView();
    }

    FogViewer fogViewer() {
        return viewer;
    }

    @Nullable
    Player viewer() {
        return viewer.privateView() ? viewer.player() : null;
    }

    Set<String> visibleSystems() {
        return visibleSystems;
    }

    Map<String, Tile> tiles() {
        return tiles;
    }

    boolean shouldConvertToGeneric(Player player) {
        return viewer.privateView() && !viewer.canSeeStats(player);
    }

    boolean canSeeStats(@Nullable Player player) {
        return viewer.canSeeStats(player);
    }

    boolean seesHomeSystem(@Nullable Player player) {
        return viewer.seesHomeSystem(player);
    }

    Set<String> seenPositions() {
        Set<String> positions = new HashSet<>();
        tiles.forEach((position, tile) -> {
            if (!isUnseenBlankFogTile(tile)) {
                positions.add(position);
            }
        });
        return positions;
    }

    boolean isUnseenBlankFogTile(Tile tile) {
        return viewer.privateView() && "0b".equals(tile.getTileID()) && StringUtils.isBlank(tile.getFogLabel(viewer()));
    }

    @Nullable
    String homeSystemPosition() {
        return viewer.privateView() && viewer.player() != null ? viewer.player().getHomeSystemPosition() : null;
    }
}
