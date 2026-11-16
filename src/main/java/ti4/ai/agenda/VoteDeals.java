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
    private static final double FREE_PICK_MARGIN = 1e-6;
    private static final int MAX_BLOC_PARTNERS = 3;
    private static final String VOTING_PHASE = "agendaVoting";
    private static final String ABSTAIN = "";

    public record Terms(String outcome, int votes, double buyerGain, double sellerCost, double chance) {

        public double surplus() {
            return buyerGain - sellerCost;
        }
    }

    public record Member(Player seller, Terms terms) {}

    public record Bloc(String outcome, List<Member> members, double buyerGain, double chance) {

        public Bloc {
            members = List.copyOf(members);
        }

        public Member first() {
            return members.getFirst();
        }
    }

    public static final class Outlook {

        private final Game game;
        private final Player viewer;
        private final AgendaForecast forecast;
        private AgendaVoting.PlannedBallot itsBallot;

        private Outlook(Game game, Player viewer) {
            this.game = game;
            this.viewer = viewer;
            forecast = AgendaForecast.of(game, viewer);
        }

        private AgendaForecast withoutDeal(Player seller) {
            if (viewer != seller) return forecast;
            return itsBallot().forecast();
        }

        private AgendaVoting.PlannedBallot itsBallot() {
            if (itsBallot == null) itsBallot = AgendaVoting.itsBallot(game, viewer);
            return itsBallot;
        }

        private String plannedVote(Player seller) {
            return viewer == seller ? itsBallot().outcome() : ABSTAIN;
        }

        private int plannedVotes(Player seller, String outcome) {
            if (viewer != seller || !AiExperiments.enabled(viewer, Experiment.AGENDA_VALUE_TABLE)) return 0;
            return itsBallot().votesOn(outcome);
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
        Predicate<String> worthPaying = freePick(outlook, seller)
                .map(free -> allowed.and(outcome -> !outcome.equalsIgnoreCase(free)))
                .orElse(allowed);
        return best(outlook, seller, buyer, worthPaying, Comparator.comparingDouble(Terms::buyerGain));
    }

    public static Optional<String> freePick(Outlook outlook, Player seller) {
        Game game = outlook.game;
        AgendaForecast forecast = outlook.forecast;
        if (forecast.isEmpty() || !votingAfter(game, seller).isEmpty()) return Optional.empty();
        int votes = AgendaVoting.castableVotes(game, seller);
        AgendaForecast abstaining = forecast.without(seller);
        Optional<String> best = abstaining.mostLikely();
        double bestValue = abstaining.expectedUtility(seller, ABSTAIN);
        for (String outcome : forecast.outcomes()) {
            AgendaForecast cast = forecast.withVotes(seller, outcome, votes);
            double value = cast.expectedUtility(seller, outcome);
            if (value > bestValue + FREE_PICK_MARGIN) {
                bestValue = value;
                best = cast.mostLikely();
            }
        }
        return best;
    }

    public static List<Bloc> blocs(
            Outlook outlook, Player buyer, Player seller, List<Player> later, Predicate<String> allowed) {
        AgendaForecast base = outlook.forecast;
        if (!votesForSale(outlook.game, base, seller)) return List.of();
        List<Player> partners = later.stream()
                .filter(partner -> partner != seller && partner != buyer)
                .filter(partner -> votesForSale(outlook.game, base, partner))
                .toList();
        if (partners.isEmpty()) return List.of();
        List<Bloc> blocs = new ArrayList<>();
        for (String outcome : base.outcomes()) {
            if (allowed.test(outcome))
                bloc(outlook, buyer, seller, partners, outcome).ifPresent(blocs::add);
        }
        return blocs;
    }

    private static Optional<Bloc> bloc(
            Outlook outlook, Player buyer, Player seller, List<Player> partners, String outcome) {
        AgendaForecast base = outlook.forecast;
        Member first = wholeStack(outlook, seller, buyer, outcome);
        List<Member> candidates = partners.stream()
                .map(partner -> wholeStack(outlook, partner, buyer, outcome))
                .filter(member -> member.terms().sellerCost() < Double.POSITIVE_INFINITY)
                .sorted(Comparator.comparingDouble(member ->
                        member.terms().sellerCost() / Math.max(1, member.terms().votes())))
                .limit(MAX_BLOC_PARTNERS)
                .toList();
        AgendaForecast joint = base.withVotes(seller, outcome, first.terms().votes());
        double before = base.expectedUtility(buyer, ABSTAIN);
        List<Member> members = new ArrayList<>(List.of(first));
        Optional<Bloc> best = Optional.empty();
        for (Member member : candidates) {
            joint = joint.withVotes(member.seller(), outcome, member.terms().votes());
            members.add(member);
            Bloc bloc =
                    new Bloc(outcome, members, joint.expectedUtility(buyer, ABSTAIN) - before, joint.chance(outcome));
            if (best.isEmpty() || surplus(bloc) > surplus(best.get())) best = Optional.of(bloc);
        }
        return best;
    }

    private static double surplus(Bloc bloc) {
        return bloc.buyerGain()
                - bloc.members().stream()
                        .mapToDouble(member -> member.terms().sellerCost())
                        .sum();
    }

    private static Member wholeStack(Outlook outlook, Player seller, Player buyer, String outcome) {
        int votes = AgendaVoting.castableVotes(outlook.game, seller);
        return new Member(seller, terms(outlook, outlook.withoutDeal(seller), seller, buyer, outcome, votes));
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

    public static boolean aboutToVote(Game game, Player seat) {
        if (PromisedVotes.hasVoted(game, seat)) return false;
        List<Player> order = AgendaForecast.votingOrder(game);
        int position = order.indexOf(seat);
        if (position < 0) return false;
        if (!VOTING_PHASE.equalsIgnoreCase(game.getPhaseOfGame())) return false;
        return position == activeVoter(game, order);
    }

    public static List<Player> votingAfter(Game game, Player seat) {
        List<Player> order = AgendaForecast.votingOrder(game);
        int position = order.indexOf(seat);
        if (position < 0) return List.of();
        return order.subList(position + 1, order.size()).stream()
                .filter(player -> !PromisedVotes.hasVoted(game, player))
                .toList();
    }

    private static int activeVoter(Game game, List<Player> order) {
        for (int voter = 0; voter < order.size(); voter++) {
            if (order.get(voter).getUserID().equals(game.getActivePlayerID())) return voter;
        }
        return -1;
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
        if (AgendaRound.isFinalAgenda(game)) return all;
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
            planets.forEach(votes -> sizes.add(votes + BallotPlanner.PREDICTIVE_VOTES));
        }
        sizes.add(most);
        return List.copyOf(sizes.headSet(most, true));
    }

    public static double spentVotes(Game game, Player viewer, int votes) {
        if (!AiExperiments.enabled(viewer, Experiment.AGENDA_VALUE_TABLE)) return 0;
        return BallotPlanner.keptVote(game, viewer) * votes;
    }

    private static Terms terms(
            Outlook outlook, AgendaForecast withoutDeal, Player seller, Player buyer, String outcome, int votes) {
        Game game = outlook.game;
        AgendaForecast withDeal = outlook.forecast.withVotes(seller, outcome, votes);
        double buyerGain = withDeal.expectedUtility(buyer, ABSTAIN) - withoutDeal.expectedUtility(buyer, ABSTAIN);
        int planned = outlook.plannedVotes(seller, outcome);
        double sellerCost = withoutDeal.expectedUtility(seller, outlook.plannedVote(seller))
                - withDeal.expectedUtility(seller, outcome)
                + (planned > 0 ? 0 : GalacticThreatCost.ofBallot(game, seller, outcome, votes))
                + spentVotes(game, outlook.viewer, Math.max(0, votes - planned));
        return new Terms(outcome, votes, buyerGain, sellerCost, withDeal.chance(outcome));
    }
}
