package ti4.ai.agenda;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.strategy.CopiedTechPolicy;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperAbilities;

@UtilityClass
public class GalacticThreatCost {

    public static final String RIDER = "Galactic Threat Rider";
    static final double MAX_COST = 3.0;

    record Prediction(Player predictor, String outcome) {}

    public static double ofBallot(Game game, Player voter, String outcome, int votes) {
        if (votes <= 0 || !AiExperiments.enabled(voter, Experiment.GALACTIC_THREAT_COST)) return 0;
        return prediction(game)
                .map(prediction -> cost(game, voter, prediction.predictor(), prediction.outcome(), outcome))
                .orElse(0.0);
    }

    static double cost(Game game, Player voter, Player predictor, String predicted, String outcome) {
        if (voter == predictor || predicted == null || !predicted.equals(outcome)) return 0;
        Set<String> alreadyOffered = new LinkedHashSet<>();
        for (Player other : othersVotedFor(game, outcome, voter, predictor)) {
            alreadyOffered.addAll(copyable(game, predictor, other));
        }
        Set<String> withVoter = new LinkedHashSet<>(alreadyOffered);
        withVoter.addAll(copyable(game, predictor, voter));
        double marginal = bestCopy(predictor, withVoter) - bestCopy(predictor, alreadyOffered);
        if (marginal <= 0) return 0;
        int rivals = Math.max(1, AgendaValueMath.liveRivals(game, voter).size());
        return Math.min(MAX_COST, marginal * AgendaValueMath.threat(game, predictor) / rivals);
    }

    static List<String> copyable(Game game, Player nekro, Player from) {
        if (from == nekro) return List.of();
        return ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(nekro, from, new ArrayList<>(), game);
    }

    static double bestCopy(Player nekro, Collection<String> aliases) {
        return aliases.stream()
                .mapToDouble(alias -> CopiedTechPolicy.publicValue(nekro, alias))
                .max()
                .orElse(0);
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

    private static Set<Player> othersVotedFor(Game game, String outcome, Player voter, Player predictor) {
        Set<Player> others = new LinkedHashSet<>();
        String placed = game.getCurrentAgendaVotes().get(outcome);
        if (placed == null) return others;
        for (String piece : placed.split(";")) {
            int split = piece.indexOf('_');
            if (split < 0) continue;
            String votes = piece.substring(split + 1);
            if (!NumberUtils.isDigits(votes) || Integer.parseInt(votes) <= 0) continue;
            Player caster = playerOf(game, piece);
            if (caster != null && caster != voter && caster != predictor) others.add(caster);
        }
        return others;
    }

    private static Player playerOf(Game game, String piece) {
        int split = piece.indexOf('_');
        if (split < 0) return null;
        return game.getPlayerFromColorOrFaction(piece.substring(0, split).toLowerCase());
    }
}
