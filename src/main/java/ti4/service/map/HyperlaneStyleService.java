package ti4.service.map;

import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.HyperlaneStyle;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
public class HyperlaneStyleService {

    static final String GAME_STYLE_KEY = "customHyperlaneStyle";

    public static HyperlaneStyle gameDefault(Game game) {
        return HyperlaneStyle.fromName(game.getStoredValue(GAME_STYLE_KEY)).orElse(HyperlaneStyle.DASHED);
    }

    public static void setGameDefault(Game game, HyperlaneStyle style) {
        game.setStoredValue(GAME_STYLE_KEY, style.name());
    }

    public static HyperlaneStyle forViewer(Game game, @Nullable Player viewer, boolean privateView) {
        if (!privateView || viewer == null || viewer.getUserID() == null) {
            return gameDefault(game);
        }
        HyperlaneStyle preferred = UserSettingsManager.get(viewer.getUserID()).getCustomHyperlaneStyle();
        return preferred != null ? preferred : gameDefault(game);
    }
}
