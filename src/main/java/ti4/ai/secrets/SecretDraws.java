package ti4.ai.secrets;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class SecretDraws {

    static final double EASY_KEEP = 0.75;

    public static boolean worthDrawing(Game game, Player seat) {
        if (hasScoredAll(seat)) return false;
        return SecretDrawValue.hasRoom(seat) || weakHand(game, seat);
    }

    public static boolean worthRedrawing(Game game, Player seat) {
        return !hasScoredAll(seat) && redrawGainPoints(game, seat) > 0;
    }

    public static double redrawValue(Game game, Player seat) {
        if (hasScoredAll(seat)) return 0;
        return SecretDrawValue.pointValue(game, seat, redrawGainPoints(game, seat));
    }

    static boolean weakHand(Game game, Player seat) {
        return !seat.getSecretsUnscored().isEmpty() && weakestKeep(game, seat) < EASY_KEEP;
    }

    static double weakestKeep(Game game, Player seat) {
        return SecretValue.lowestValueSecret(game, seat)
                .map(secret -> SecretValue.keepValue(game, seat, secret))
                .orElse(0.0);
    }

    private static double redrawGainPoints(Game game, Player seat) {
        if (!weakHand(game, seat)) return 0;
        return Math.max(0, SecretDrawValue.swapPoints(game, seat) - weakestKeep(game, seat));
    }

    private static boolean hasScoredAll(Player seat) {
        return seat.getSoScored() >= seat.getMaxSOCount();
    }
}
