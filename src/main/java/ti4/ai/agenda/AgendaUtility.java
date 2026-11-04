package ti4.ai.agenda;

import java.util.OptionalDouble;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class AgendaUtility {

    private static final double POLICY_UNIT_CAP = 2;
    private static final double POLICY_UNIT_TG = 4;
    private static final int POINT = 1;

    static double of(Game game, Player voter, boolean readsOwnSecrets, String agenda, String outcome) {
        OptionalDouble value = AgendaValues.of(game, voter, agenda, outcome, outcome);
        if (value.isPresent()) return value.getAsDouble() + driveTheDebate(game, voter, readsOwnSecrets, outcome);
        if (AgendaPolicy.givesElectedPlayerAPoint(agenda)) return pointElection(game, voter, outcome);
        double policy = readsOwnSecrets
                ? AgendaPolicy.score(game, voter, agenda, outcome)
                : AgendaPolicy.publicScore(game, voter, agenda, outcome);
        return Math.clamp(policy, -POLICY_UNIT_CAP, POLICY_UNIT_CAP) * POLICY_UNIT_TG;
    }

    static double riderStake(Game game, Player rider) {
        return ScoringStakes.rivalStake(game, rider, POINT);
    }

    private static double driveTheDebate(Game game, Player voter, boolean readsOwnSecrets, String outcome) {
        if (!readsOwnSecrets || !AgendaPolicy.holdsDriveTheDebateFor(game, voter, outcome)) return 0;
        return ScoringStakes.scoreValue(game, voter, POINT);
    }

    private static double pointElection(Game game, Player voter, String outcome) {
        Player elected = game.getPlayerFromColorOrFaction(outcome);
        if (elected == null || !game.getRealPlayers().contains(elected)) return 0;
        if (elected == voter) return ScoringStakes.scoreValue(game, voter, POINT);
        return -ScoringStakes.rivalStake(game, elected, POINT);
    }
}
