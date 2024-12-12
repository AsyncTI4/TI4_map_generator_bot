package ti4.service.player;

import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.Player;

@UtilityClass
public class PlayerReactService {

    public static boolean checkForASpecificPlayerReact(String messageId, Player player, Game game) {
        boolean foundReact = false;
        try {
            if (game.getStoredValue(messageId) != null && game.getStoredValue(messageId).contains(player.getFaction())) {
                return true;
            }
        } catch (Exception e) {
            game.removeMessageIDForSabo(messageId);
            return true;
        }
        return foundReact;
    }
}
