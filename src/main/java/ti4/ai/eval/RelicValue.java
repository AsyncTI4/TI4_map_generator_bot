package ti4.ai.eval;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;

@UtilityClass
public class RelicValue {

    public static final double BASE = 3.5;
    static final double FULL_ROUNDS = 3;
    static final double LATE_SHARE = 0.4;

    public static double draw(Game game) {
        List<String> deck = game.getAllRelics();
        if (deck != null && deck.isEmpty()) return 0;
        double roundsLeft = GameHorizon.roundsLeft(game);
        return BASE * Math.clamp(roundsLeft / FULL_ROUNDS, LATE_SHARE, 1);
    }
}
