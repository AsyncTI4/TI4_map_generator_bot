package ti4.ai.agenda;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class VoteDeals {

    private static final double ENOUGH = 0.1;

    public record Terms(String outcome, int votes, double buyerGain, double sellerCost) {

        public double surplus() {
            return buyerGain - sellerCost;
        }
    }

    public static final class Outlook {

        private final Game game;
        private final Player viewer;
        private final AgendaForecast forecast;
        private AgendaForecast withItsBallot;

        private Outlook(Game game, Player viewer) {
            this.game = game;
            this.viewer = viewer;
            forecast = AgendaForecast.of(game, viewer);
        }

        private AgendaForecast withoutDeal(Player seller) {
            if (viewer != seller) return forecast;
            if (withItsBallot == null) withItsBallot = AgendaVoting.forecastWithItsBallot(game, seller);
            return withItsBallot;
        }
    }

    public static Outlook outlook(Game game, Player viewer) {
        return new Outlook(game, viewer);
    }

    public static Optional<Terms> bestSale(Outlook outlook, Player seller, Player buyer, Predicate<String> allowed) {
        return best(outlook, seller, buyer, allowed, Comparator.comparingDouble(Terms::surplus));
    }

    public static Optional<Terms> bestPurchase(
            Outlook outlook, Player buyer, Player seller, Predicate<String> allowed) {
        return best(outlook, seller, buyer, allowed, Comparator.comparingDouble(Terms::buyerGain));
    }

    public static Optional<Terms> valued(Game game, Player viewer, Player seller, Player buyer, String outcome) {
        Outlook outlook = outlook(game, viewer);
        if (!sellable(outlook, seller, outcome)) return Optional.empty();
        return Optional.of(valued(outlook, outlook.withoutDeal(seller), seller, buyer, outcome));
    }

    public static Optional<Terms> valuedAt(
            Game game, Player viewer, Player seller, Player buyer, String outcome, int promisedVotes) {
        Outlook outlook = outlook(game, viewer);
        if (!sellable(outlook, seller, outcome)) return Optional.empty();
        int castable = AgendaVoting.castableVotes(game, seller);
        int votes = promisedVotes > 0 ? Math.min(promisedVotes, castable) : castable;
        return Optional.of(terms(outlook, outlook.withoutDeal(seller), seller, buyer, outcome, votes));
    }

    private static boolean sellable(Outlook outlook, Player seller, String outcome) {
        return votesForSale(outlook.game, outlook.forecast, seller)
                && outlook.forecast.outcomes().contains(outcome);
    }

    private static Optional<Terms> best(
            Outlook outlook, Player seller, Player buyer, Predicate<String> allowed, Comparator<Terms> preference) {
        if (!votesForSale(outlook.game, outlook.forecast, seller)) return Optional.empty();
        AgendaForecast withoutDeal = outlook.withoutDeal(seller);
        return outlook.forecast.outcomes().stream()
                .filter(allowed)
                .map(outcome -> valued(outlook, withoutDeal, seller, buyer, outcome))
                .max(preference);
    }

    private static boolean votesForSale(Game game, AgendaForecast forecast, Player seller) {
        return !forecast.isEmpty()
                && forecast.votesLeft(seller) > 0
                && AgendaVoting.castableVotes(game, seller) > 0
                && !PromisedVotes.hasVoted(game, seller);
    }

    private static Terms valued(
            Outlook outlook, AgendaForecast withoutDeal, Player seller, Player buyer, String outcome) {
        Game game = outlook.game;
        int most = AgendaVoting.castableVotes(game, seller);
        Terms all = terms(outlook, withoutDeal, seller, buyer, outcome, most);
        if (!AiExperiments.enabled(outlook.viewer, Experiment.AGENDA_VALUE_TABLE)) return all;
        List<Terms> sizes = new ArrayList<>();
        for (int votes : saleSizes(game, seller, most)) {
            sizes.add(votes == most ? all : terms(outlook, withoutDeal, seller, buyer, outcome, votes));
        }
        double best = sizes.stream().mapToDouble(Terms::surplus).max().orElse(all.surplus());
        return sizes.stream()
                .filter(terms -> terms.surplus() >= best - ENOUGH)
                .findFirst()
                .orElse(all);
    }

    static List<Integer> saleSizes(Game game, Player seller, int most) {
        if (most <= 0) return List.of();
        List<Integer> planets = BallotPlanner.reachableTotals(AgendaVoting.readiedPlanetVotes(game, seller), most);
        TreeSet<Integer> sizes = new TreeSet<>(planets);
        if (AgendaVoting.plansPredictive(seller)) {
            sizes.add(BallotPlanner.PREDICTIVE_VOTES);
            planets.forEach(votes -> sizes.add(votes + BallotPlanner.PREDICTIVE_VOTES));
        }
        sizes.add(most);
        return List.copyOf(sizes.headSet(most, true));
    }

    static double spentVotes(Game game, Player viewer, int votes) {
        if (!AiExperiments.enabled(viewer, Experiment.AGENDA_VALUE_TABLE)) return 0;
        return BallotPlanner.keptVote(game, viewer) * votes;
    }

    private static Terms terms(
            Outlook outlook, AgendaForecast withoutDeal, Player seller, Player buyer, String outcome, int votes) {
        Game game = outlook.game;
        AgendaForecast withDeal = outlook.forecast.withVotes(seller, outcome, votes);
        double buyerGain = withDeal.expectedUtility(buyer) - withoutDeal.expectedUtility(buyer);
        double sellerCost = withoutDeal.expectedUtilityWithoutThreatDislike(seller)
                - withDeal.expectedUtilityWithoutThreatDislike(seller)
                + GalacticThreatCost.ofBallot(game, seller, outcome, votes)
                + spentVotes(game, outlook.viewer, votes);
        return new Terms(outcome, votes, buyerGain, sellerCost);
    }
}
