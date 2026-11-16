package ti4.ai.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.agenda.AgendaForecast;
import ti4.ai.agenda.AgendaVoting;
import ti4.ai.agenda.BoatFloat;
import ti4.ai.agenda.PromisedVotes;
import ti4.ai.agenda.VoteDeals;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.ai.promise.PromisePayment;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class AvoidanceBribes {

    static final Set<String> AGENDAS = Set.of("execution", "redistribution");
    static final double MIN_LOSS = 2.0;
    static final double MIN_CHANCE = 0.1;
    static final int MAX_PER_AGENDA = 2;
    private static final String ABSTAIN = "";
    private static final String VOTING_PHASE = "agendaVoting";
    private static final String CONSIDERED_KEY = "avoidanceBribeConsidered|";

    record Ballot(AgendaForecast forecast, String outcome, double value) {}

    static boolean active(Player seat) {
        return PromiseDeals.conditional(seat) && AiExperiments.enabled(seat, Experiment.AVOIDANCE_BRIBES);
    }

    static Optional<AiDecision> propose(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player payer = context.seat();
        if (!applies(game, payer) || offeredThisAgenda(game, payer) >= MAX_PER_AGENDA) return Optional.empty();
        String considered = CONSIDERED_KEY + game.getCurrentAgendaInfo() + "|" + game.getActivePlayerID() + "|"
                + payer.getTg() + "|" + game.getCurrentAgendaVotes();
        if (context.memory().has(considered)) return Optional.empty();
        context.memory().put(considered, "1");
        AgendaForecast forecast = AgendaForecast.of(game, payer);
        Optional<String> dreaded = dreaded(forecast, payer);
        if (dreaded.isEmpty()) return Optional.empty();
        return partners.stream()
                .filter(seller -> seller != payer && !PromisedVotes.hasVoted(game, seller))
                .filter(seller -> !PromiseDeals.sellingVotes(game, seller))
                .filter(seller -> PromiseDeals.approachableForVotes(context, seller))
                .flatMap(seller ->
                        offer(game, forecast, payer, seller, dreaded.get(), Trust.of(context, seller)).stream())
                .max(Comparator.comparingDouble(PromiseDeals.Proposal::gain))
                .flatMap(proposal -> PromiseDeals.postProposal(context, proposal));
    }

    private static boolean applies(Game game, Player payer) {
        return active(payer)
                && VOTING_PHASE.equalsIgnoreCase(game.getPhaseOfGame())
                && AGENDAS.contains(PromisedVotes.agendaKey(game))
                && !PromisedVotes.agendaKey(game).isBlank()
                && BoatFloat.active(game).isEmpty();
    }

    static Optional<String> dreaded(AgendaForecast forecast, Player payer) {
        if (forecast.isEmpty() || forecast.outcomes().size() < 2) return Optional.empty();
        return forecast.outcomes().stream()
                .filter(outcome -> forecast.chance(outcome) >= MIN_CHANCE)
                .filter(outcome -> lossFrom(forecast, payer, outcome) >= MIN_LOSS)
                .min(Comparator.comparingDouble(outcome -> forecast.utility(payer, outcome)));
    }

    static double lossFrom(AgendaForecast forecast, Player payer, String outcome) {
        double others = forecast.outcomes().stream()
                .filter(other -> !other.equalsIgnoreCase(outcome))
                .mapToDouble(other -> forecast.utility(payer, other))
                .average()
                .orElse(0);
        return others - forecast.utility(payer, outcome);
    }

    static Optional<PromiseDeals.Proposal> offer(
            Game game, AgendaForecast forecast, Player payer, Player seller, String dreaded, double trust) {
        return stake(game, forecast, payer, seller, dreaded)
                .flatMap(stake -> AnswererDeals.proposal(game, payer, seller, stake, trust, Promise.Term.NOT_FOR));
    }

    private static Optional<AnswererDeals.Stake> stake(
            Game game, AgendaForecast forecast, Player payer, Player seller, String dreaded) {
        if (!sells(game, forecast, seller)) return Optional.empty();
        Ballot free = best(game, forecast, seller, "");
        if (!dreaded.equalsIgnoreCase(free.outcome())) return Optional.empty();
        Ballot avoiding = best(game, forecast, seller, dreaded);
        double gain = avoiding.forecast().expectedUtility(payer, ABSTAIN)
                - free.forecast().expectedUtility(payer, ABSTAIN);
        if (gain <= 0) return Optional.empty();
        double cost = Math.max(0, free.value() - avoiding.value());
        double chance = 1 - avoiding.forecast().chance(dreaded);
        return Optional.of(new AnswererDeals.Stake(dreaded, 0, gain, cost, chance));
    }

    static boolean accepts(AiTurnContext context, Promise promise, Player payer, double margin) {
        Game game = context.game();
        Player seller = context.seat();
        if (!active(seller) || !promise.proposer().equals(payer.getFaction())) return false;
        if (PromisedVotes.binding(game, seller)
                .filter(bound -> bound.target().equalsIgnoreCase(promise.target()))
                .isPresent()) {
            return false;
        }
        AgendaForecast forecast = AgendaForecast.of(game, seller);
        if (!sells(game, forecast, seller)) return false;
        Ballot free = best(game, forecast, seller, "");
        Ballot avoiding = best(game, forecast, seller, promise.target());
        double cost = Math.max(0, free.value() - avoiding.value());
        double chance = 1 - avoiding.forecast().chance(promise.target());
        if (chance < PromiseDeals.MIN_PAY_CHANCE) return false;
        double received = chance
                * PaymentValue.received(
                        game, seller, payer, PromisePayment.of(game, promise), Trust.of(context, payer));
        return received - cost >= margin;
    }

    static Optional<String> explainOnItsAgendas(Game game, Player payer) {
        if (!AGENDAS.contains(PromisedVotes.agendaKey(game))) return Optional.empty();
        return Optional.of(explain(game, payer));
    }

    static String explain(Game game, Player payer) {
        return explain(game, payer, game.getRealPlayers());
    }

    static String explain(Game game, Player payer, List<Player> sellers) {
        if (!active(payer)) return "not in use";
        AgendaForecast forecast = AgendaForecast.of(game, payer);
        Optional<String> dreaded = dreaded(forecast, payer);
        if (dreaded.isEmpty()) {
            return String.format(
                    "no outcome both %.0f%%+ likely and %.0f TG+ worse than the others", 100 * MIN_CHANCE, MIN_LOSS);
        }
        String target = dreaded.get();
        List<String> parts = new ArrayList<>();
        for (Player seller : sellers) {
            if (seller == payer || PromisedVotes.hasVoted(game, seller) || !sells(game, forecast, seller)) continue;
            parts.add(StringUtils.capitalize(seller.getFaction()) + ": "
                    + sellerNote(game, forecast, payer, seller, target));
        }
        return String.format(
                        "dreads %s (%.1f TG worse than the others, %.0f%% likely)",
                        target, lossFrom(forecast, payer, target), 100 * forecast.chance(target))
                + (parts.isEmpty() ? "" : "; " + String.join("; ", parts));
    }

    private static String sellerNote(Game game, AgendaForecast forecast, Player payer, Player seller, String target) {
        Ballot free = best(game, forecast, seller, "");
        if (!target.equalsIgnoreCase(free.outcome())) {
            return "best ballot is " + (free.outcome().isBlank() ? "abstain" : free.outcome());
        }
        Optional<AnswererDeals.Stake> stake = stake(game, forecast, payer, seller, target);
        if (stake.isEmpty()) return "votes " + target + "; barring it gains nothing";
        String terms = String.format(
                "votes %s; barring it gains %.1f TG, costs it %.1f TG, %.0f%% %s loses",
                target,
                stake.get().payerGain(),
                stake.get().answererCost(),
                100 * stake.get().chance(),
                target);
        return AnswererDeals.proposal(game, payer, seller, stake.get(), 1.0, Promise.Term.NOT_FOR)
                .map(proposal -> terms + "; would offer " + proposal.payment().describe())
                .orElseGet(() -> terms + "; no payment fits (" + PromiseDeals.spendableTradeGoods(game, payer)
                        + " TG spendable)");
    }

    public static Optional<String> explainAt(Game game, Player payer, Player seller) {
        if (!AGENDAS.contains(PromisedVotes.agendaKey(game)) || payer == seller) return Optional.empty();
        AgendaForecast forecast = AgendaForecast.of(game, payer);
        if (dreaded(forecast, payer).isEmpty()) return Optional.empty();
        return Optional.of(explain(game, payer, List.of(seller)));
    }

    private static boolean sells(Game game, AgendaForecast forecast, Player seller) {
        return !forecast.isEmpty()
                && forecast.votesLeft(seller) > 0
                && AgendaVoting.castableVotes(game, seller) > 0
                && !PromisedVotes.hasVoted(game, seller);
    }

    static Ballot best(Game game, AgendaForecast forecast, Player seller, String barred) {
        int votes = AgendaVoting.castableVotes(game, seller);
        AgendaForecast abstaining = forecast.without(seller);
        Ballot best = new Ballot(abstaining, ABSTAIN, abstaining.expectedUtility(seller, ABSTAIN));
        double spent = VoteDeals.spentVotes(game, seller, votes);
        for (String outcome : forecast.outcomes()) {
            if (outcome.equalsIgnoreCase(barred)) continue;
            AgendaForecast cast = forecast.withVotes(seller, outcome, votes);
            double value = cast.expectedUtility(seller, outcome) - spent;
            if (value > best.value()) best = new Ballot(cast, outcome, value);
        }
        return best;
    }

    private static long offeredThisAgenda(Game game, Player payer) {
        return PromiseBook.proposedBy(game, payer).stream()
                .filter(promise -> promise.term() == Promise.Term.NOT_FOR && promise.isPromisee(payer))
                .filter(promise -> PromisedVotes.onThisAgenda(game, promise))
                .count();
    }
}
