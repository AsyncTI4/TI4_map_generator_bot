package ti4.image.tile;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import ti4.game.Game;
import ti4.helpers.FoWHelper;
import ti4.model.HyperlaneStyle;
import ti4.service.fow.FogViewer;
import ti4.service.map.BridgeMatrix;
import ti4.service.map.HyperlaneStyleService;
import ti4.service.option.FOWOptionService.FOWOption;

final class HyperlaneView {

    private final Game game;
    private final FogViewer viewer;
    private HyperlaneStyle style;
    private Set<String> visibleLanes;
    private Set<String> revealedPositions;
    private final Map<String, BridgeMatrix> drawnBridges = new HashMap<>();

    HyperlaneView(Game game, FogViewer viewer) {
        this.game = game;
        this.viewer = viewer;
    }

    synchronized HyperlaneStyle style() {
        if (style == null) {
            style = HyperlaneStyleService.forViewer(game, viewer.player(), viewer.privateView());
        }
        return style;
    }

    boolean hidesUnseenLanes() {
        return viewer.privateView() && viewer.player() != null && game.getFowOption(FOWOption.HIDE_UNSEEN_HYPERLANES);
    }

    synchronized Set<String> visibleLanes() {
        if (visibleLanes == null) {
            visibleLanes = FoWHelper.getVisibleHyperlaneLanes(game, viewer.player());
        }
        return visibleLanes;
    }

    synchronized BridgeMatrix drawnBridge(String key, Supplier<BridgeMatrix> compute) {
        BridgeMatrix drawn = drawnBridges.get(key);
        if (drawn == null) {
            drawn = compute.get();
            drawnBridges.put(key, drawn);
        }
        return drawn;
    }

    synchronized Set<String> revealedPositions() {
        if (revealedPositions == null) {
            revealedPositions = FoWHelper.getDirectlyRevealedPositions(game, viewer.player());
        }
        return revealedPositions;
    }
}
