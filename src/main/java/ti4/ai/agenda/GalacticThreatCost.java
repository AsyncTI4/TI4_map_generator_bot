package ti4.ai.agenda;

import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class GalacticThreatCost {

    public static final String RIDER = "Galactic Threat Rider";
    static final double ALONE = 1.0;
    static final double JOINED = 0.5;

    record Prediction(Player predictor, String outcome) {}

    public static double ofBallot(Game game, Player voter, String outcome, int votes) {
        if (votes <= 0 || !AiExperiments.enabled(voter, Experiment.GALACTIC_THREAT_COST)) return 0;
        return prediction(game)
                .map(prediction -> cost(game, voter, prediction.predictor(), prediction.outcome(), outcome))
                .orElse(0.0);
    }

    static double cost(Game game, Player voter, Player predictor, String predicted, String outcome) {
        if (voter == predictor || predicted == null || !predicted.equals(outcome)) return 0;
        return othersVotedFor(game, outcome, voter, predictor) ? JOINED : ALONE;
    }

    static Optional<Prediction> prediction(Game game) {
        for (Map.Entry<String, String> entry : game.getCurrentAgendaVotes().entrySet()) {
            for (String piece : entry.getValue().split(";")) {
                if (!piece.endsWith("_" + RIDER)) continue;
                Player predictor = playerOf(game, piece);
                if (predictor != null) return Optional.of(new Prediction(predictor, entry.getKey()));
            }
        }
        return Optional.empty();
    }

    private static boolean othersVotedFor(Game game, String outcome, Player voter, Player predictor) {
        String placed = game.getCurrentAgendaVotes().get(outcome);
        if (placed == null) return false;
        for (String piece : placed.split(";")) {
            int split = piece.indexOf('_');
            if (split < 0) continue;
            String votes = piece.substring(split + 1);
            if (!NumberUtils.isDigits(votes) || Integer.parseInt(votes) <= 0) continue;
            Player caster = playerOf(game, piece);
            if (caster != null && caster != voter && caster != predictor) return true;
        }
        return false;
    }

    private static Player playerOf(Game game, String piece) {
        int split = piece.indexOf('_');
        if (split < 0) return null;
        return game.getPlayerFromColorOrFaction(piece.substring(0, split).toLowerCase());
    }
}
