package ti4.ai.agenda;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.SearchMemo;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.DeckModel;
import ti4.model.Mapper;

@UtilityClass
public class AgendaPrior {

    static final double MIN_VOTE = 0.02;
    static final double MAX_VOTE = 1.0;
    static final double PIVOTAL_SHARE = 0.25;
    private static final String COVERT = "covert";
    private static final String SPREAD_MEMO = "agendaPrior.spread";
    private static final String EXPECTED_MEMO = "agendaPrior.expected";
    private static final String DECK_SPREAD_MEMO = "agendaPrior.deckSpread";
    private static final String DECK_EXPECTED_MEMO = "agendaPrior.deckExpected";

    static List<String> remainingDeck(Game game) {
        DeckModel deck = Mapper.getDeck(game.getAgendaDeckID());
        if (deck == null) return List.of();
        Set<String> gone = new HashSet<>(game.getDiscardAgendas().keySet());
        gone.addAll(game.getLaws().keySet());
        gone.add(AgendaPolicy.currentAgenda(game));
        gone.add(COVERT);
        return deck.getNewDeck().stream()
                .distinct()
                .filter(alias -> !gone.contains(alias))
                .filter(alias -> Mapper.getAgenda(alias) != null)
                .sorted()
                .toList();
    }

    static double spread(Game game, Player seat, String agenda) {
        return SearchMemo.remember(() -> computeSpread(game, seat, agenda), SPREAD_MEMO, seat, agenda);
    }

    private static double computeSpread(Game game, Player seat, String agenda) {
        List<String> outcomes = AgendaOutcomes.of(game, agenda);
        if (outcomes.size() < 2) return 0;
        double most = Double.NEGATIVE_INFINITY;
        double least = Double.POSITIVE_INFINITY;
        for (String outcome : outcomes) {
            double value = AgendaUtility.of(game, seat, true, agenda, outcome);
            most = Math.max(most, value);
            least = Math.min(least, value);
        }
        return most - least;
    }

    public static double expected(Game game, Player seat, String agenda) {
        return SearchMemo.remember(() -> computeExpected(game, seat, agenda), EXPECTED_MEMO, seat, agenda);
    }

    private static double computeExpected(Game game, Player seat, String agenda) {
        List<String> outcomes = AgendaOutcomes.of(game, agenda);
        if (outcomes.isEmpty()) return 0;
        double[] support = new double[outcomes.size()];
        for (Player voter : game.getRealPlayers()) {
            int votes = VoteCapacity.of(game, voter);
            if (votes <= 0) continue;
            support[favourite(game, voter, seat, agenda, outcomes)] += votes;
        }
        double total = 0;
        double weighted = 0;
        for (int outcome = 0; outcome < outcomes.size(); outcome++) {
            double weight = support[outcome];
            total += weight;
            weighted += weight * AgendaUtility.of(game, seat, true, agenda, outcomes.get(outcome));
        }
        if (total <= 0) {
            return outcomes.stream()
                    .mapToDouble(outcome -> AgendaUtility.of(game, seat, true, agenda, outcome))
                    .average()
                    .orElse(0);
        }
        return weighted / total;
    }

    private static int favourite(Game game, Player voter, Player seat, String agenda, List<String> outcomes) {
        int best = 0;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (int outcome = 0; outcome < outcomes.size(); outcome++) {
            double value = AgendaUtility.of(game, voter, voter == seat, agenda, outcomes.get(outcome));
            if (value > bestValue) {
                bestValue = value;
                best = outcome;
            }
        }
        return best;
    }

    public static double deckExpected(Game game, Player seat) {
        return SearchMemo.remember(
                () -> deckAverage(game, agenda -> expected(game, seat, agenda)), DECK_EXPECTED_MEMO, seat);
    }

    static double fullDeckSpread(Game game, Player seat) {
        return SearchMemo.remember(
                () -> deckAverage(game, agenda -> spread(game, seat, agenda)), DECK_SPREAD_MEMO, seat);
    }

    private static double deckAverage(Game game, ToDoubleFunction<String> value) {
        return remainingDeck(game).stream().mapToDouble(value).average().orElse(0);
    }

    static double marginalVote(Game game, Player seat) {
        return perVote(game, seat, AgendaKnowledge.next(game, seat));
    }

    public static double phaseVoteValue(Game game, Player seat) {
        List<String> known = AgendaKnowledge.knownTop(game, seat);
        Optional<String> first = known.stream().findFirst();
        Optional<String> second = known.stream().skip(1).findFirst();
        return perVote(game, seat, first) + perVote(game, seat, second);
    }

    private static double perVote(Game game, Player seat, Optional<String> agenda) {
        double spread = agenda.map(known -> spread(game, seat, known)).orElseGet(() -> fullDeckSpread(game, seat));
        int tableVotes = game.getRealPlayers().stream()
                .mapToInt(player -> VoteCapacity.of(game, player))
                .sum();
        if (tableVotes <= 0) return MIN_VOTE;
        return Math.clamp(PIVOTAL_SHARE * spread / tableVotes, MIN_VOTE, MAX_VOTE);
    }
}
