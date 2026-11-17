package ti4.ai.scoring;

import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.eval.GameHorizon;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class Desperation {

    public static final double FACTOR = 1.5;
    public static final int STATUS_SECRET_ROUND = 3;

    public static boolean enabled(Player seat) {
        return AiExperiments.enabled(seat, Experiment.DESPERATION);
    }

    public static boolean forActionSecrets(Game game, Player seat) {
        return enabled(seat) && (game.getRound() >= GameHorizon.lateRound(game) || aRivalCanWin(game, seat));
    }

    public static double actionSecretFactor(Game game, Player seat) {
        return forActionSecrets(game, seat) ? FACTOR : 1.0;
    }

    public static boolean forStatusSecretSetup(Game game, Player seat, boolean statusSecretReady) {
        return enabled(seat) && game.getRound() >= STATUS_SECRET_ROUND && !statusSecretReady;
    }

    public static boolean forPublic(Player seat, boolean publicReady) {
        return enabled(seat) && !publicReady;
    }

    private static boolean aRivalCanWin(Game game, Player seat) {
        return game.getRealPlayers().stream()
                .filter(player -> !player.equals(seat))
                .anyMatch(rival -> WinRace.canWinThisRound(game, seat, rival));
    }
}
