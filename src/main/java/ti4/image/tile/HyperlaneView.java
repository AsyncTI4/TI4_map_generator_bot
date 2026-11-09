package ti4.image.tile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import ti4.game.Game;
import ti4.helpers.FoWHelper;
import ti4.model.HyperlaneStyle;
import ti4.service.fow.FogViewer;
import ti4.service.map.HyperlaneBridgeEnds.BridgeEnd;
import ti4.service.map.HyperlaneStyleService;
import ti4.service.option.FOWOptionService.FOWOption;

final class HyperlaneView {

    private final Game game;
    private final FogViewer viewer;
    private HyperlaneStyle style;
    private Set<String> visibleLanes;
    private Set<String> revealedPositions;
    private final Map<String, List<BridgeEnd>> bridgeEnds = new HashMap<>();

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

    synchronized List<BridgeEnd> bridgeEnds(String key, Supplier<List<BridgeEnd>> compute) {
        List<BridgeEnd> ends = bridgeEnds.get(key);
        if (ends == null) {
            ends = compute.get();
            bridgeEnds.put(key, ends);
        }
        return ends;
    }

    synchronized Set<String> revealedPositions() {
        if (revealedPositions == null) {
            revealedPositions = FoWHelper.getDirectlyRevealedPositions(game, viewer.player());
        }
        return revealedPositions;
    }
}
