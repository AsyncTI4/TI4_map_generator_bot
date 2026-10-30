package ti4.ai.strategy;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class SpeakerTokenValue {

    public static double of(Game game, Player seat) {
        return PoliticsUses.tokenValue(game, seat);
    }
}
