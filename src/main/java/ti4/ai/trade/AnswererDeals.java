package ti4.ai.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.agenda.AgendaForecast;
import ti4.ai.agenda.AgendaVoting;
import ti4.ai.agenda.DiscardBounties;
import ti4.ai.agenda.PromisedVotes;
import ti4.ai.agenda.VoteDeals;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class AnswererDeals {

    private static final String ABSTAIN = "";
    private static final String CONSIDERED_KEY = "answererDealConsidered|";
    private static final String VOTING_PHASE = "agendaVoting";
    static final double GENEROUS_SHARE = 0.75;

    record Stake(String outcome, int votes, double payerGain, double answererCost, double chance) {}

    static boolean active(Player seat) {
        return PromiseDeals.conditional(seat) && AiExperiments.enabled(seat, Experiment.PAY_THE_ANSWERER);
    }

    static Optional<AiDecision> propose(AiTurnContext context, Supplier<List<Player>> partners) {
        Game game = context.game();
        Player payer = context.seat();
        if (!active(payer) || PromisedVotes.agendaKey(game).isBlank()) return Optional.empty();
        if (!VoteDeals.aboutToVote(game, payer) || offeredThisAgenda(game, payer)) return Optional.empty();
        String considered = CONSIDERED_KEY + game.getCurrentAgendaInfo() + "|" + game.getPhaseOfGame() + "|"
                + game.getActivePlayerID() + "|" + payer.getTg() + "|" + game.getCurrentAgendaVotes();
        if (context.memory().has(considered)) return Optional.empty();
        context.memory().put(considered, "1");
        Predicate<String> backed = PromiseDeals.backedOutcome(context);
        return partners.get().stream()
                .filter(partner -> PromiseDeals.approachableForVotes(context, partner))
                .flatMap(partner -> offer(game, payer, partner, backed, Trust.of(context, partner)).stream())
                .max(Comparator.comparingDouble(PromiseDeals.Proposal::gain))
                .flatMap(proposal -> PromiseDeals.postProposal(context, proposal));
    }

    static Optional<PromiseDeals.Proposal> offer(
            Game game, Player payer, Player answerer, Predicate<String> backed, double trust) {
        if (!active(payer) || !VoteDeals.votingAfter(game, payer).contains(answerer)) return Optional.empty();
        int votes = AgendaVoting.castableVotes(game, payer);
        if (votes <= 0 || PromisedVotes.hasVoted(game, payer)) return Optional.empty();
        AgendaVoting.PlannedBallot planned = AgendaVoting.itsBallot(game, payer);
        AgendaForecast forecast = AgendaForecast.of(game, payer);
        if (forecast.isEmpty() || forecast.votesLeft(answerer) <= 0) return Optional.empty();
        double baseline = planned.value(payer);
        if (-baseline < DiscardBounties.MIN_LOSS) return Optional.empty();
        return forecast.outcomes().stream()
                .filter(backed)
                .map(outcome -> stake(game, forecast, planned, payer, answerer, outcome, votes, baseline))
                .flatMap(Optional::stream)
                .flatMap(stake -> proposal(game, payer, answerer, stake, trust).stream())
                .max(Comparator.comparingDouble(PromiseDeals.Proposal::gain));
    }

    static String explain(Game game, Player payer) {
        if (!active(payer)) return "not in use";
        if (PromisedVotes.hasVoted(game, payer)) return "already voted";
        int votes = AgendaVoting.castableVotes(game, payer);
        if (votes <= 0) return "no votes to cast";
        List<Player> answerers = VoteDeals.votingAfter(game, payer);
        if (answerers.isEmpty()) return "nobody votes after it";
        AgendaForecast forecast = AgendaForecast.of(game, payer);
        if (forecast.isEmpty()) return "no forecast";
        AgendaVoting.PlannedBallot planned = AgendaVoting.itsBallot(game, payer);
        double baseline = planned.value(payer);
        if (-baseline < DiscardBounties.MIN_LOSS) {
            return String.format(
                    "its planned ballot leaves it at %+.1f TG; it pays only to avoid a loss of %.0f TG or more",
                    baseline, DiscardBounties.MIN_LOSS);
        }
        List<String> parts = new ArrayList<>();
        for (Player answerer : answerers) {
            parts.add(StringUtils.capitalize(answerer.getFaction()) + ": "
                    + explainAnswerer(game, forecast, planned, payer, answerer, votes, baseline));
        }
        return String.format("planned ballot at %+.1f TG; ", baseline) + String.join("; ", parts);
    }

    private static String explainAnswerer(
            Game game,
            AgendaForecast forecast,
            AgendaVoting.PlannedBallot planned,
            Player payer,
            Player answerer,
            int votes,
            double baseline) {
        if (forecast.votesLeft(answerer) <= 0) return "no votes left";
        Optional<Stake> best = forecast.outcomes().stream()
                .map(outcome -> stake(game, forecast, planned, payer, answerer, outcome, votes, baseline))
                .flatMap(Optional::stream)
                .max(Comparator.comparingDouble(Stake::payerGain));
        if (best.isEmpty()) return "its staying out gains nothing";
        Stake stake = best.get();
        String terms = String.format(
                "staying out on %s gains %.1f TG, costs it %.1f TG, %.0f%% to resolve",
                stake.outcome(), stake.payerGain(), stake.answererCost(), 100 * stake.chance());
        return proposal(game, payer, answerer, stake, 1.0)
                .map(proposal -> terms + "; would offer " + proposal.price() + " TG")
                .orElseGet(() ->
                        terms + "; no price fits (" + PromiseDeals.spendableTradeGoods(game, payer) + " TG spendable)");
    }

    private static Optional<Stake> stake(
            Game game,
            AgendaForecast forecast,
            AgendaVoting.PlannedBallot planned,
            Player payer,
            Player answerer,
            String outcome,
            int votes,
            double baseline) {
        double spent = VoteDeals.spentVotes(game, payer, votes);
        AgendaForecast answered = forecast.withVotes(payer, outcome, votes);
        AgendaForecast dealt = answered.without(answerer);
        double unanswered = Math.max(baseline, answered.expectedUtility(payer, outcome) - spent);
        double gain = dealt.expectedUtility(payer, outcome) - spent - unanswered;
        if (gain <= 0) return Optional.empty();
        double cost = planned.forecast().expectedUtility(answerer) - dealt.expectedUtility(answerer, ABSTAIN);
        return Optional.of(new Stake(outcome, votes, gain, cost, dealt.chance(outcome)));
    }

    private static Optional<PromiseDeals.Proposal> proposal(
            Game game, Player payer, Player answerer, Stake stake, double trust) {
        double value = trust * stake.payerGain();
        double cost = stake.answererCost();
        double chance = stake.chance();
        int spendable = PromiseDeals.spendableTradeGoods(game, payer);
        return PromiseDeals.buyerPrice(value, cost, answerer, spendable, 0, 0, chance)
                .map(least -> generousShare(least, mostWorthPaying(value, chance, spendable)))
                .map(price -> new PromiseDeals.Proposal(
                        Promise.Term.NOT_AGAINST, answerer, payer, stake.outcome(), 0, price, value - chance * price));
    }

    private static int mostWorthPaying(double value, double chance, int spendable) {
        return Math.min(spendable, (int) Math.floor((value - TradeValue.PROPOSE_MIN) / chance));
    }

    private static int generousShare(int least, int most) {
        if (most <= least) return least;
        return least + (int) Math.ceil(GENEROUS_SHARE * (most - least));
    }

    static boolean accepts(AiTurnContext context, Promise promise, Player payer, double margin) {
        Game game = context.game();
        Player answerer = context.seat();
        if (!active(answerer) || !promise.proposer().equals(payer.getFaction())) return false;
        if (!PromiseDeals.backedOutcome(context).test(promise.target())) return false;
        Optional<Stake> stake = answererView(game, answerer, payer, promise.target());
        if (stake.isEmpty() || stake.get().chance() < PromiseDeals.MIN_PAY_CHANCE) return false;
        double received = stake.get().chance() * Trust.of(context, payer) * promise.price();
        return received - stake.get().answererCost() >= margin;
    }

    static Optional<Stake> answererView(Game game, Player answerer, Player payer, String outcome) {
        AgendaForecast forecast = AgendaForecast.of(game, answerer);
        int votes = AgendaVoting.castableVotes(game, payer);
        if (forecast.isEmpty() || votes <= 0) return Optional.empty();
        double unpaid = AgendaVoting.itsBallot(game, answerer).value(answerer);
        AgendaForecast dealt = forecast.withVotes(payer, outcome, votes).without(answerer);
        double cost = unpaid - dealt.expectedUtility(answerer, ABSTAIN);
        return Optional.of(new Stake(outcome, votes, 0, cost, dealt.chance(outcome)));
    }

    static Optional<AiDecision> awaitAnswer(AiTurnContext context) {
        Game game = context.game();
        Player payer = context.seat();
        if (!active(payer) || !VOTING_PHASE.equalsIgnoreCase(game.getPhaseOfGame()) || !context.isActivePlayer()) {
            return Optional.empty();
        }
        return openOffer(game, payer)
                .filter(promise -> promise.expiresAt() > context.now())
                .map(promise -> new AiDecision.Wait(promise.expiresAt(), "wait for an answer to its offer"));
    }

    private static Optional<Promise> openOffer(Game game, Player payer) {
        return PromiseBook.all(game).stream()
                .filter(promise -> promise.term() == Promise.Term.NOT_AGAINST && promise.open())
                .filter(promise ->
                        promise.isPromisee(payer) && promise.proposer().equals(payer.getFaction()))
                .filter(promise -> PromisedVotes.onThisAgenda(game, promise))
                .findFirst();
    }

    private static boolean offeredThisAgenda(Game game, Player payer) {
        return PromiseBook.proposedBy(game, payer).stream()
                .filter(promise -> promise.term() == Promise.Term.NOT_AGAINST && promise.isPromisee(payer))
                .anyMatch(promise -> PromisedVotes.onThisAgenda(game, promise));
    }
}
