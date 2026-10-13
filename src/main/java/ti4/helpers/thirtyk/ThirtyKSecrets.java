package ti4.helpers.thirtyk;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ThirtyKSecrets {

    public static boolean isAutoChecked(String objID) {
        return threshold(objID) > 0;
    }

    public static int threshold(String objID) {
        return 0;
    }

    public static int progress(String objID, Game game, Player player) {
        return 0;
    }
}
