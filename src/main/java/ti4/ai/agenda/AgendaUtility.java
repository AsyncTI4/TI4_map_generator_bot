package ti4.ai.agenda;

import java.util.OptionalDouble;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.AgendaModel;
import ti4.model.Mapper;

@UtilityClass
class AgendaUtility {

    private static final double POLICY_UNIT_CAP = 2;
    private static final double POLICY_UNIT_TG = 4;
    private static final int POINT = 1;
    private static final String DICTATE_POLICY = "dp";
    private static final int LAWS_FOR_DICTATE_POLICY = 3;
    private static final double LATER_LAW_CHANCE = 0.5;
    private static final String LAW = "Law";
    private static final String FOR_AGAINST = "For/Against";

    static double of(Game game, Player voter, boolean readsOwnSecrets, String agenda, String outcome) {
        return of(game, voter, readsOwnSecrets, AgendaValues.usesExpertTable(voter), agenda, outcome);
    }

    static double of(
            Game game, Player voter, boolean readsOwnSecrets, boolean expertTable, String agenda, String outcome) {
        double dictate = readsOwnSecrets ? dictatePolicy(game, voter, agenda, outcome) : 0;
        OptionalDouble value = AgendaValues.of(game, voter, readsOwnSecrets, expertTable, agenda, outcome, outcome);
        if (value.isPresent()) {
            return value.getAsDouble() + driveTheDebate(game, voter, readsOwnSecrets, outcome) + dictate;
        }
        if (AgendaPolicy.givesElectedPlayerAPoint(agenda)) return pointElection(game, voter, outcome) + dictate;
        double policy = readsOwnSecrets
                ? AgendaPolicy.score(game, voter, agenda, outcome)
                : AgendaPolicy.publicScore(game, voter, agenda, outcome);
        return Math.clamp(policy, -POLICY_UNIT_CAP, POLICY_UNIT_CAP) * POLICY_UNIT_TG + dictate;
    }

    static double dictatePolicy(Game game, Player voter, String agenda, String outcome) {
        if (!AgendaValueMath.isFor(outcome) || !enactsOnlyOnFor(agenda)) return 0;
        if (!voter.getSecretsUnscored().containsKey(DICTATE_POLICY)) return 0;
        if (voter.getSoScored() >= voter.getMaxSOCount()) return 0;
        int stillNeeded = LAWS_FOR_DICTATE_POLICY - game.getLaws().size() - 1;
        if (stillNeeded < 0) return 0;
        return Math.pow(LATER_LAW_CHANCE, stillNeeded) * ScoringStakes.scoreValue(game, voter, POINT);
    }

    private static boolean enactsOnlyOnFor(String agenda) {
        AgendaModel model = agenda == null || agenda.isBlank() ? null : Mapper.getAgenda(agenda);
        return model != null
                && LAW.equalsIgnoreCase(model.getType())
                && model.getTarget() != null
                && model.getTarget().contains(FOR_AGAINST);
    }

    static double riderStake(Game game, Player rider) {
        return ScoringStakes.rivalPointStake(game, rider, POINT);
    }

    private static double driveTheDebate(Game game, Player voter, boolean readsOwnSecrets, String outcome) {
        if (!readsOwnSecrets || !AgendaPolicy.holdsDriveTheDebateFor(game, voter, outcome)) return 0;
        return ScoringStakes.scoreValue(game, voter, POINT);
    }

    private static double pointElection(Game game, Player voter, String outcome) {
        Player elected = game.getPlayerFromColorOrFaction(outcome);
        if (elected == null || !game.getRealPlayers().contains(elected)) return 0;
        if (elected == voter) return ScoringStakes.scoreValue(game, voter, POINT);
        return -ScoringStakes.rivalPointStake(game, elected, POINT);
    }
}
