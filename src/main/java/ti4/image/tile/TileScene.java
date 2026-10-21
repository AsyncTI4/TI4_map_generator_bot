package ti4.image.tile;

import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.service.fow.FogViewer;

record TileScene(Game game, @Nullable DisplayType displayType, FogViewer viewer) {

    boolean isPrivateView() {
        return viewer.privateView();
    }

    @Nullable
    Player fowPlayer() {
        return viewer.player();
    }

    boolean hidesFogged(Tile tile) {
        return isPrivateView() && tile.hasFog(fowPlayer());
    }

    boolean showsGeneric(@Nullable Player owner) {
        return isPrivateView() && !viewer.canSeeStats(owner);
    }
}
