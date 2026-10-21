package ti4.image;

import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;

record TileScene(Game game, @Nullable DisplayType displayType, TileViewer viewer) {

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
        return isPrivateView() && !FoWHelper.canSeeStatsOfPlayer(game, owner, fowPlayer());
    }
}
