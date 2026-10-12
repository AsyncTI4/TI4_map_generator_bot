package ti4.ai.strategy;

import java.util.Collection;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ReadyingValue {

    public static double of(Game game, Player seat, Collection<String> planets) {
        if (planets.isEmpty()) return 0;
        return CardValueScale.READY_SHARE
                * DiplomacyUse.readying(game, seat, planets).use();
    }
}
