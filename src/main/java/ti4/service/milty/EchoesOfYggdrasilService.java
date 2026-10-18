package ti4.service.milty;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.service.draft.DraftTileManager;

@UtilityClass
public class EchoesOfYggdrasilService {

    private static final List<String> TILE_IDS = List.of("ao1", "ao2", "ao3", "ao4", "ao5", "ao6", "ao7", "ao8", "ao9");

    public static void addTiles(Game game, MiltyDraftManager draftManager) {
        if (!isEnabled(game)) return;
        TILE_IDS.forEach(tileId -> MiltyDraftHelper.addDraftTile(draftManager, tileId));
    }

    public static void addTiles(Game game, DraftTileManager draftManager) {
        if (!isEnabled(game)) return;
        TILE_IDS.forEach(draftManager::addDraftTile);
    }

    private static boolean isEnabled(Game game) {
        return Boolean.parseBoolean(game.getStoredValue(Constants.INCLUDE_ECHOES_OF_YGGDRASIL_TILES));
    }
}
