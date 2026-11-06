package ti4.image.tile;

import java.util.Set;
import ti4.game.Game;
import ti4.helpers.FoWHelper;
import ti4.model.HyperlaneStyle;
import ti4.service.fow.FogViewer;
import ti4.service.map.HyperlaneStyleService;
import ti4.service.option.FOWOptionService.FOWOption;

final class HyperlaneView {

    private final Game game;
    private final FogViewer viewer;
    private HyperlaneStyle style;
    private Set<String> visibleLanes;
    private Set<String> revealedPositions;

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

    synchronized Set<String> revealedPositions() {
        if (revealedPositions == null) {
            revealedPositions = FoWHelper.getDirectlyRevealedPositions(game, viewer.player());
        }
        return revealedPositions;
    }
}
