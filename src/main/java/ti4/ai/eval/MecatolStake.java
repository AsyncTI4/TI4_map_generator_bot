package ti4.ai.eval;

import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.secrets.SecretDraws;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class MecatolStake {

    static final double IMPERIAL_ROUND_SHARE = 0.25;
    private static final int MAX_IMPERIAL_ROUNDS = 2;
    private static final Set<String> SECRET_REDRAW_MECATOLS = Set.of("mrte", "mc");

    public static double holdValue(Game game, Player player) {
        return ObjectiveValue.VICTORY_POINT_VALUE * IMPERIAL_ROUND_SHARE * imperialRoundsAhead(game);
    }

    public static double ownHoldValue(Game game, Player seat) {
        return holdValue(game, seat) + redrawValue(game, seat);
    }

    public static double redrawValue(Game game, Player seat) {
        return redrawsSecrets(game) ? SecretDraws.redrawValue(game, seat) : 0;
    }

    private static boolean redrawsSecrets(Game game) {
        Tile mecatol = game.getMecatolTile();
        return mecatol != null && mecatol.getUnitHolders().keySet().stream().anyMatch(SECRET_REDRAW_MECATOLS::contains);
    }

    private static int imperialRoundsAhead(Game game) {
        return Math.clamp(GameHorizon.roundsLeft(game) - 1, 0, MAX_IMPERIAL_ROUNDS);
    }
}
