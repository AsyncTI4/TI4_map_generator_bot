package ti4.service.game;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.map.persistence.ManagedGame;

@UtilityClass
public class ManagedGameService {

    public String getGameNameForSorting(ManagedGame game) {
        String gameName = game.getName();
        if (gameName.startsWith("pbd")) {
            return StringUtils.leftPad(gameName, 10, "0");
        }
        if (gameName.startsWith("fow")) {
            return StringUtils.leftPad(gameName, 10, "1");
        }
        return gameName;
    }
}
