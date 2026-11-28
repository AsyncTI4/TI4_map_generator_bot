package ti4.ai.scoring;

import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.eval.GameHorizon;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class Desperation {

    public static final double FACTOR = 1.2;
    public static final double BEHIND_FACTOR = 1.5;
    public static final int POINTS_BEHIND = 2;
    public static final int PUBLIC_ROUND = 2;
    public static final int STATUS_SECRET_ROUND = 3;
    private static final Set<String> AFTER_THIS_ROUNDS_SCORING =
            Set.of("statushomework", "agenda", "agendawaiting", "agendavoting", "agendaend");

    public static double factor(Game game, Player seat) {
        return fallenBehind(game, seat) || aRivalCanWin(game, seat) ? BEHIND_FACTOR : FACTOR;
    }

    public static boolean forPublic(Game game, Player seat, boolean publicReady) {
        return AiExperiments.enabled(seat, Experiment.DESPERATION_PUBLIC)
                && !publicReady
                && scoringRound(game) >= PUBLIC_ROUND;
    }

    public static boolean forStatusSecretSetup(Game game, Player seat, boolean statusSecretReady) {
        return AiExperiments.enabled(seat, Experiment.DESPERATION_STATUS_SECRET)
                && !statusSecretReady
                && scoringRound(game) >= STATUS_SECRET_ROUND;
    }

    public static boolean forActionSecrets(Game game, Player seat) {
        return AiExperiments.enabled(seat, Experiment.DESPERATION_ACTION_SECRET)
                && (scoringRound(game) >= GameHorizon.lateRound(game) || aRivalCanWin(game, seat));
    }

    public static double actionSecretFactor(Game game, Player seat) {
        return forActionSecrets(game, seat) ? factor(game, seat) : 1.0;
    }

    static int scoringRound(Game game) {
        String phase = game.getPhaseOfGame();
        boolean scoredThisRound = phase != null && AFTER_THIS_ROUNDS_SCORING.contains(phase.toLowerCase());
        return scoredThisRound ? game.getRound() + 1 : game.getRound();
    }

    static boolean fallenBehind(Game game, Player seat) {
        int own = seat.getTotalVictoryPoints();
        return game.getRealPlayers().stream()
                .filter(player -> !player.equals(seat))
                .anyMatch(rival -> rival.getTotalVictoryPoints() >= own + POINTS_BEHIND);
    }

    private static boolean aRivalCanWin(Game game, Player seat) {
        return game.getRealPlayers().stream()
                .filter(player -> !player.equals(seat))
                .anyMatch(rival -> WinRace.canWinThisRound(game, seat, rival));
    }
}
