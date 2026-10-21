package ti4.image;

import ti4.game.Game;
import ti4.helpers.DisplayType;
import ti4.service.option.FOWOptionService.FOWOption;

final class MapSideStatsSection {

    private MapSideStatsSection() {}

    static boolean drawsPlayerInfo(Game game, DisplayType displayType, FogView fog) {
        return displayType != DisplayType.stats
                && !PlayerAreaGenerator.isMegaGame(game)
                && (!fog.isPrivate() || !game.getFowOption(FOWOption.HIDE_PLAYER_INFOS));
    }
}
