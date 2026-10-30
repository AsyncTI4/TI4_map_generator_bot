package ti4.ai.secrets;

import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSettings;
import ti4.ai.eval.GameHorizon;
import ti4.ai.tactical.CannonWipeRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ActionSecretTiming {

    private static final double SHARED_COMBAT_FACTOR = 0.85;
    private static final double PATIENCE_CONFIDENCE = 0.4;
    private static final Set<String> NEVER_SAVED = Set.of("mew", "dtgs");

    public static boolean pursueNow(Game game, Player seat, String secretId) {
        return NEVER_SAVED.contains(secretId)
                || !AiSettings.isSecretHoldEnabled()
                || GameHorizon.endInSight(game)
                || holdsAStandingDustTarget(game, seat, secretId)
                || worriedAboutTheBoard(game, seat, secretId);
    }

    private static boolean holdsAStandingDustTarget(Game game, Player seat, String secretId) {
        return CannonWipeRules.TURN_THEIR_FLEETS_TO_DUST.equals(secretId)
                && CannonWipeRules.hasCoveredTarget(game, seat);
    }

    private static boolean worriedAboutTheBoard(Game game, Player seat, String secretId) {
        double perRound = ActionSecretOdds.lastingPerRound(game, seat, secretId)
                * Math.pow(SHARED_COMBAT_FACTOR, competingSecrets(seat, secretId));
        double chanceLater = ActionSecretOdds.atLeastOnce(perRound, GameHorizon.roundsBeforeEnd(game) + 1);
        return chanceLater < PATIENCE_CONFIDENCE;
    }

    private static long competingSecrets(Player seat, String secretId) {
        return seat.getSecretsUnscored().keySet().stream()
                .filter(ActionSecretRules.COMBAT_SECRETS::contains)
                .filter(other -> !other.equals(secretId))
                .count();
    }
}
