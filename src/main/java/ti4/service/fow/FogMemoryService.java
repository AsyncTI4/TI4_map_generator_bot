package ti4.service.fow;

import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;

public final class FogMemoryService {

    private FogMemoryService() {}

    public static void refresh(Game game, FogViewer viewer) {
        Player player = viewer.player();
        if (!game.isFowMode() || player == null) {
            return;
        }
        if (viewer.privateView()) {
            FoWHelper.updateFog(game, player);
        } else {
            FoWHelper.ensureFogInitialized(game, player);
        }
    }

    public static void ensureInitialized(Game game, @Nullable Player player) {
        if (game.isFowMode() && player != null) {
            FoWHelper.ensureFogInitialized(game, player);
        }
    }

    public static void refreshForPlayerOverview(Game game, @Nullable Player player) {
        if (player != null) {
            FoWHelper.updateFog(game, player);
        }
    }
}
