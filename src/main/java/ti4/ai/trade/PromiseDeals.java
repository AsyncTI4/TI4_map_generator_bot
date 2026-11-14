package ti4.ai.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiSeats;
import ti4.ai.actioncards.AgendaCardRules;
import ti4.ai.agenda.AgendaQueue;
import ti4.ai.agenda.AgendaRound;
import ti4.ai.agenda.AgendaVoting;
import ti4.ai.agenda.GalacticThreatCost;
import ti4.ai.agenda.PromisedVotes;
import ti4.ai.agenda.VoteDeals;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.ai.promise.PromiseChecks;
import ti4.ai.promise.PromisePayment;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class PromiseDeals {

    static final String ACCEPT_PREFIX = "aiPromiseYes_";
    static final String DECLINE_PREFIX = "aiPromiseNo_";
    static final int MAX_PER_SCOPE = Extortion.MAX_PER_ROUND;
    static final int MAX_TERRITORY_PRICE = Extortion.MAX_DEMAND;
    static final String PAY_PREFIX = "aiPromisePay_";
    static final int MAX_BLOC_OFFERS = 4;
    static final double MIN_PAY_CHANCE = 0.2;
    private static final int MIN_PRICE = 1;
    static final int MARKET_PREMIUM = 1;
    static final int PREDICTION_VOTE_PRICE_CAP = 2;
    private static final String PAY_POSTED_KEY = "promisePayPosted|";
    private static final Comparator<Proposal> BEST = Comparator.comparingDouble(Proposal::gain)
            .thenComparing(proposal -> proposal.term() == Promise.Term.STAY_OUT);

    private static final Comparator<Proposal> LIGHTEST_VOTE_DEAL = Comparator.comparingDouble(Proposal::gain)
            .thenComparing(proposal -> proposal.term() == Promise.Term.NOT_AGAINST);

    record Proposal(
            Promise.Term term,
            Player promiser,
            Player promisee,
            String target,
            int votes,
            int price,
            double gain,
            PromisePayment payment) {

        Proposal(
                Promise.Term term, Player promiser, Player promisee, String target, int votes, int price, double gain) {
            this(term, promiser, promisee, target, votes, price, gain, PromisePayment.tradeGoods(price));
        }
    }

    static boolean active(Player seat) {
        return AiExperiments.enabled(seat, Experiment.PROMISE_DEALS);
    }

    static Optional<AiDecision> proposeOwnTurn(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        if (!active(seat) || !TradeLegality.isActionPhase(game)) return Optional.empty();
        if (proposedThisScope(game, seat, false) >= MAX_PER_SCOPE) return Optional.empty();
        Function<Player, List<TacticalPlan>> plans = rankedOnce(game);
        return partners.stream()
                .filter(partner -> approachable(context, partner))
                .filter(partner -> noTerritorialDealYet(game, seat, partner))
                .flatMap(partner -> territorial(context, plans, partner).stream())
                .max(BEST)
                .flatMap(proposal -> post(context, proposal));
    }

    static Optional<AiDecision> proposeVotes(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        if (!active(seat) || !TradeLegality.isAgendaPhase(game)) return Optional.empty();
        if (PromisedVotes.agendaKey(game).isBlank()) return Optional.empty();
        if (proposedThisScope(game, seat, true) >= maxVoteOffers(seat)) return Optional.empty();
        List<Player> open = partners.stream()
                .filter(partner -> approachable(context, partner))
                .filter(partner -> noVoteDealYet(game, seat, partner))
                .toList();
        if (open.isEmpty()) return Optional.empty();
        VoteDeals.Outlook outlook = VoteDeals.outlook(game, seat);
        boolean mayPromiseVotes = !votesCommittedElsewhere(context);
        Predicate<String> backed = backedOutcome(context);
        return open.stream()
                .flatMap(partner -> votes(context, outlook, mayPromiseVotes, backed, partner).stream())
                .max(LIGHTEST_VOTE_DEAL)
                .flatMap(proposal -> post(context, proposal));
    }

    static boolean conditional(Player proposer) {
        return active(proposer) && AiExperiments.enabled(proposer, Experiment.CONDITIONAL_VOTE_DEALS);
    }

    static boolean paysOnResolution(Game game, Promise promise) {
        if (!promise.aboutVotes()) return false;
        Player proposer = game.getPlayerFromColorOrFaction(promise.proposer());
        return proposer != null && AiSeats.isAiSeat(proposer) && conditional(proposer);
    }

    private static int maxVoteOffers(Player seat) {
        return conditional(seat) ? MAX_BLOC_OFFERS : MAX_PER_SCOPE;
    }

    static double payChance(boolean conditional, VoteDeals.Terms terms) {
        return conditional ? terms.chance() : 1;
    }

    static Optional<AiDecision> answer(AiTurnContext context) {
        Player seat = context.seat();
        if (!active(seat)) return Optional.empty();
        Game game = context.game();
        Optional<AiDecision> payment = payOwed(context);
        if (payment.isPresent()) return payment;
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            for (PromptButton button : prompt.enabledButtons()) {
                if (!button.isOwnedBy(context.faction())
                        || !button.handlerId().startsWith(ACCEPT_PREFIX)
                        || context.alreadyPressed(prompt, button)) {
                    continue;
                }
                String id = StringUtils.removeStart(button.handlerId(), ACCEPT_PREFIX);
                Optional<Promise> promise = openFor(game, seat, id);
                if (promise.isEmpty()) continue;
                if (accepts(context, promise.get())) {
                    if (!paysOnResolution(game, promise.get())) {
                        proposerOf(game, promise.get())
                                .ifPresent(proposer -> TradeLegality.useWindow(context, proposer));
                    }
                    return Optional.of(AiDecision.press(prompt, button, "accept the promise deal"));
                }
                Optional<PromptButton> decline = prompt.firstEnabled(candidate -> candidate.isOwnedBy(context.faction())
                        && candidate.handlerId().equals(DECLINE_PREFIX + id));
                if (decline.isPresent() && !context.alreadyPressed(prompt, decline.get())) {
                    return Optional.of(AiDecision.press(prompt, decline.get(), "decline the promise deal"));
                }
            }
        }
        return postOwed(context);
    }

    private static Optional<AiDecision> payOwed(AiTurnContext context) {
        Game game = context.game();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            for (PromptButton button : prompt.enabledButtons()) {
                if (!button.isOwnedBy(context.faction())
                        || !button.handlerId().startsWith(PAY_PREFIX)
                        || context.alreadyPressed(prompt, button)) {
                    continue;
                }
                Optional<Promise> owed =
                        owedBy(game, context.seat(), StringUtils.removeStart(button.handlerId(), PAY_PREFIX));
                if (owed.isEmpty() || !PromisePayment.of(game, owed.get()).affordable(context.seat())) continue;
                Player payee = owed.get().promiserIn(game).orElseThrow();
                if (!TradeLegality.canTransact(game, context.seat(), payee)
                        || TradeLegality.windowUsed(context, payee)) {
                    continue;
                }
                TradeLegality.useWindow(context, payee);
                return Optional.of(AiDecision.press(prompt, button, "pay what it owes for the agenda deal"));
            }
        }
        return Optional.empty();
    }

    static Optional<Promise> owedBy(Game game, Player payer, String id) {
        return PromiseBook.find(game, id)
                .filter(promise -> promise.status() == Promise.Status.OWED || promise.status() == Promise.Status.UNPAID)
                .filter(promise -> promise.isPromisee(payer))
                .filter(promise -> promise.promiserIn(game).isPresent());
    }

    private static Optional<AiDecision> postOwed(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        for (Promise promise : PromiseBook.all(game)) {
            if (promise.status() != Promise.Status.OWED) continue;
            Optional<Player> payee = promise.promiserIn(game);
            Optional<Player> payer = promise.promiseeIn(game);
            if (payee.isEmpty() || payer.isEmpty() || !postsTheBill(seat, payer.get(), payee.get())) continue;
            if (!TradeLegality.canTransact(game, payer.get(), payee.get())) continue;
            if (seat == payer.get() && TradeLegality.windowUsed(context, payee.get())) continue;
            String key = PAY_POSTED_KEY + promise.id();
            if (context.memory().has(key)) continue;
            Optional<String> channel = channelId(context);
            if (channel.isEmpty()) continue;
            context.memory().put(key, String.valueOf(context.now()));
            return Optional.of(new AiDecision.Post(
                    channel.get(),
                    billText(game, payer.get(), payee.get(), promise),
                    payButtons(game, payer.get(), promise)));
        }
        return Optional.empty();
    }

    private static boolean postsTheBill(Player seat, Player payer, Player payee) {
        if (seat == payer) return true;
        return seat == payee && !AiSeats.isAiSeat(payer);
    }

    static String billText(Game game, Player payer, Player payee, Promise promise) {
        String reason =
                switch (promise.term()) {
                    case DISCARD_AGENDA -> "for discarding the agenda with " + promise.target();
                    case NOT_AGAINST ->
                        "for not voting against **" + outcomeName(game, promise.target()) + "**, which won";
                    default -> "for its votes on **" + outcomeName(game, promise.target()) + "**, which won";
                };
        return payer.getRepresentationUnfogged() + " owes " + payee.getFactionEmojiOrColor() + " "
                + PromisePayment.of(game, promise).describe() + " " + reason + ".";
    }

    private static List<Button> payButtons(Game game, Player payer, Promise promise) {
        String owner = ComponentIdEnvelope.ownedBy(payer.getFaction());
        return List.of(Buttons.green(
                owner + PAY_PREFIX + promise.id(),
                "Pay " + PromisePayment.of(game, promise).label()));
    }

    static void observe(AiTurnContext context) {
        Player seat = context.seat();
        if (!active(seat)) return;
        Game game = context.game();
        for (Promise promise : PromiseBook.all(game)) {
            Optional<Promise.Status> status = settled(context, promise);
            if (status.isPresent()) {
                PromiseBook.persist(
                        game,
                        status.get() == Promise.Status.OWED
                                ? promise.owing(context.now() + Promise.OWED_GRACE_MILLIS)
                                : promise.with(status.get()));
            } else if (promise.isPromisee(seat)) {
                PromiseChecks.withoutLeftSystems(game, promise).ifPresent(pruned -> PromiseBook.persist(game, pruned));
            }
        }
    }

    static Optional<Promise.Status> settled(AiTurnContext context, Promise promise) {
        Game game = context.game();
        Player seat = context.seat();
        if (promise.open() && promise.proposer().equals(seat.getFaction())) {
            boolean lapsed = context.now() >= promise.expiresAt() || !stillOpen(game, promise);
            return lapsed ? Optional.of(Promise.Status.EXPIRED) : Optional.empty();
        }
        if (!promise.isPromisee(seat) && !promise.isPromiser(seat)) return Optional.empty();
        if (promise.status() == Promise.Status.OWED) {
            return promise.isPromiser(seat) && leftUnpaid(context, promise)
                    ? Optional.of(Promise.Status.UNPAID)
                    : Optional.empty();
        }
        if (promise.status() == Promise.Status.KEPT && paysOnResolution(game, promise)) {
            return PromisedVotes.settlement(game, promise);
        }
        if (promise.status() != Promise.Status.ACCEPTED) return Optional.empty();
        return promise.aboutVotes() ? PromisedVotes.verdict(game, promise) : PromiseChecks.territorial(game, promise);
    }

    private static boolean leftUnpaid(AiTurnContext context, Promise promise) {
        Game game = context.game();
        Optional<Player> payer = promise.promiseeIn(game);
        Optional<Player> payee = promise.promiserIn(game);
        if (payer.isEmpty() || payee.isEmpty() || context.now() < promise.expiresAt()) return false;
        boolean sameAgenda = promise.thisRound(game) && promise.agenda().equals(PromisedVotes.agendaKey(game));
        return !sameAgenda && TradeLegality.canTransact(game, payer.get(), payee.get());
    }

    static boolean billPending(Game game, Player seat, Player partner) {
        return PromiseBook.all(game).stream()
                .filter(promise -> promise.status() == Promise.Status.OWED)
                .anyMatch(promise -> PromiseBook.between(promise, seat, partner));
    }

    static int promisedTradeGoods(Game game, Player buyer, String exceptId) {
        return PromiseBook.all(game).stream()
                .filter(promise -> promise.isPromisee(buyer) && !promise.id().equals(exceptId))
                .filter(promise -> stillToPay(game, promise))
                .mapToInt(Promise::price)
                .sum();
    }

    private static boolean stillToPay(Game game, Promise promise) {
        if (promise.status() == Promise.Status.OWED) return true;
        if (!paysOnResolution(game, promise) || !PromisedVotes.onThisAgenda(game, promise)) return false;
        return promise.open() || promise.status() == Promise.Status.ACCEPTED || promise.status() == Promise.Status.KEPT;
    }

    static int spendableTradeGoods(Game game, Player buyer) {
        return Math.max(0, buyer.getTg() - promisedTradeGoods(game, buyer, ""));
    }

    static Optional<Promise> openFor(Game game, Player party, String id) {
        return PromiseBook.find(game, id)
                .filter(Promise::open)
                .filter(promise -> !promise.proposer().equals(party.getFaction()))
                .filter(promise -> promise.isPromiser(party) || promise.isPromisee(party))
                .filter(promise -> stillOpen(game, promise));
    }

    static boolean offerOpenWith(Game game, Player seat, Player partner) {
        if (!active(seat)) return false;
        return PromiseBook.all(game).stream()
                .filter(Promise::open)
                .filter(promise -> PromiseBook.between(promise, seat, partner))
                .anyMatch(promise -> stillOpen(game, promise));
    }

    static boolean stillOpen(Game game, Promise promise) {
        if (!promise.thisRound(game)) return false;
        Optional<Player> promiser = promise.promiserIn(game);
        if (promiser.isEmpty() || promise.promiseeIn(game).isEmpty()) return false;
        if (promise.aboutVotes()) {
            return PromisedVotes.onThisAgenda(game, promise) && !PromisedVotes.hasVoted(game, promiser.get());
        }
        Optional<Player> proposer = Optional.ofNullable(game.getPlayerFromColorOrFaction(promise.proposer()));
        return TradeLegality.isActionPhase(game)
                && proposer.filter(player -> player.getUserID().equals(game.getActivePlayerID()))
                        .isPresent();
    }

    static boolean accepts(AiTurnContext context, Promise promise) {
        Game game = context.game();
        Player seat = context.seat();
        Player promiser = promise.promiserIn(game).orElse(null);
        Player promisee = promise.promiseeIn(game).orElse(null);
        if (promiser == null || promisee == null) return false;
        Player proposer = promise.isPromiser(seat) ? promisee : promiser;
        if (ScoringStakes.contender(game, seat, proposer)) return false;
        if (!TradeLegality.canTransact(game, seat, proposer)
                || TradeLegality.windowUsed(context, proposer)
                || billPending(game, seat, proposer)) {
            return false;
        }
        double margin = TradeValue.margin(proposer);
        if (promise.isPromiser(seat)) return sells(context, promise, promisee, margin);
        if (promise.aboutVotes()
                && overPredictionCap(game, seat, seat, promise.target(), promise.price(), promise.id())) {
            return false;
        }
        double paid = payChance(game, seat, promiser, promise) * promise.price();
        double gain = Trust.of(context, promiser) * protection(game, seat, promiser, promise);
        int spendable = seat.getTg() - promisedTradeGoods(game, seat, promise.id());
        return spendable >= promise.price() && gain - paid >= margin;
    }

    private static double payChance(Game game, Player viewer, Player seller, Promise promise) {
        if (!paysOnResolution(game, promise)) return 1;
        Player buyer = promise.promiseeIn(game).orElse(viewer);
        if (promise.term() == Promise.Term.NOT_AGAINST) {
            return RiderExtortion.toHolder(game, buyer, seller, promise.target())
                    .map(RiderExtortion.Stake::chance)
                    .orElse(0.0);
        }
        return VoteDeals.valuedAt(game, viewer, seller, buyer, promise.target(), promise.votes())
                .map(VoteDeals.Terms::chance)
                .orElse(0.0);
    }

    private static boolean sells(AiTurnContext context, Promise promise, Player buyer, double margin) {
        Game game = context.game();
        Player seat = context.seat();
        if (promise.term() == Promise.Term.NOT_AGAINST) return AnswererDeals.accepts(context, promise, buyer, margin);
        boolean votes = promise.term() == Promise.Term.VOTE_FOR;
        if (votes
                && (votesCommittedElsewhere(context) || !backedOutcome(context).test(promise.target()))) {
            return false;
        }
        double cost = restraint(game, seat, promise);
        double chance = votes ? payChance(game, seat, seat, promise) : 1;
        if (chance < MIN_PAY_CHANCE) return false;
        double worth = votes
                ? VoteDeals.valuedAt(game, seat, seat, buyer, promise.target(), promise.votes())
                        .map(VoteDeals.Terms::buyerGain)
                        .orElse(0.0)
                : PromiseValue.protection(game, buyer, seat, promise.term(), promise.target());
        double received = chance * (paysOnResolution(game, promise) ? Trust.of(context, buyer) : 1);
        Optional<Integer> asked =
                sellerPrice(worth, cost, buyer, buyer.getTg(), votes ? 0 : MAX_TERRITORY_PRICE, chance);
        int ask = votes ? sellerAsk(game, seat, cost, chance, asked.orElse(0)) : asked.orElse(0);
        return promise.price() >= ask && received * promise.price() - cost >= margin;
    }

    private static int sellerAsk(Game game, Player seller, double cost, double chance, int monopoly) {
        if (!marketPrices(seller)) return Math.max(monopoly, voteFloor(cost, chance));
        return marketAsk(game, cost, chance, monopoly);
    }

    static boolean marketPrices(Player seat) {
        return AiExperiments.enabled(seat, Experiment.AGENDA_VALUE_TABLE);
    }

    static int predictionCap(Game game, Player buyer, String outcome) {
        return predictionCap(game, buyer, buyer, outcome);
    }

    static int predictionCap(Game game, Player flagSeat, Player buyer, String outcome) {
        OptionalInt budget = predictionBudget(game, flagSeat, buyer, outcome, "");
        return budget.isPresent() ? Math.max(MIN_PRICE, budget.getAsInt()) : 0;
    }

    private static boolean overPredictionCap(
            Game game, Player flagSeat, Player buyer, String outcome, int price, String exceptId) {
        OptionalInt budget = predictionBudget(game, flagSeat, buyer, outcome, exceptId);
        return budget.isPresent() && price > budget.getAsInt();
    }

    private static OptionalInt predictionBudget(
            Game game, Player flagSeat, Player buyer, String outcome, String exceptId) {
        if (!marketPrices(flagSeat) || !GalacticThreatCost.predicts(game, buyer, outcome)) return OptionalInt.empty();
        return OptionalInt.of(Math.max(0, PREDICTION_VOTE_PRICE_CAP - promisedToward(game, buyer, outcome, exceptId)));
    }

    static int promisedToward(Game game, Player buyer, String outcome, String exceptId) {
        return PromiseBook.all(game).stream()
                .filter(promise -> promise.isPromisee(buyer) && promise.aboutVotes())
                .filter(promise -> !promise.id().equals(exceptId))
                .filter(promise -> PromisedVotes.onThisAgenda(game, promise))
                .filter(promise -> promise.target().equalsIgnoreCase(outcome))
                .filter(promise -> committedBy(buyer, promise))
                .mapToInt(Promise::price)
                .sum();
    }

    private static boolean committedBy(Player buyer, Promise promise) {
        if (promise.open()) return promise.proposer().equals(buyer.getFaction());
        return switch (promise.status()) {
            case ACCEPTED, KEPT, OWED, PAID -> true;
            default -> false;
        };
    }

    static int marketAsk(Game game, double cost, double chance, int monopoly) {
        int floor = voteFloor(cost, chance);
        if (AgendaRound.isFinalAgenda(game) || cost <= 0) return floor;
        return Math.max(floor, Math.min(monopoly, floor + MARKET_PREMIUM));
    }

    private static int voteAsk(Game game, Player seller, Player buyer, VoteDeals.Terms terms, double chance) {
        int monopoly = askOf(seller, terms.buyerGain(), terms.sellerCost(), buyer, 0, chance);
        if (!marketPrices(buyer) || !AiSeats.isAiSeat(seller)) return monopoly;
        return marketAsk(game, terms.sellerCost(), chance, monopoly);
    }

    static int voteFloor(double sellerCost) {
        return voteFloor(sellerCost, 1);
    }

    static int voteFloor(double sellerCost, double payChance) {
        return Math.max(MIN_PRICE, (int) Math.ceil((sellerCost + TradeValue.PROPOSE_MIN) / payChance));
    }

    static Predicate<String> backedOutcome(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<String> backed = AgendaVoting.ballotInProgress(context)
                .or(() -> PromisedVotes.binding(game, seat).map(Promise::target))
                .or(() -> PromisedVotes.restraint(game, seat).map(Promise::target))
                .or(() -> openSaleTarget(game, seat));
        return backed.<Predicate<String>>map(outcome -> outcome::equalsIgnoreCase)
                .orElse(outcome -> true);
    }

    private static Optional<String> openSaleTarget(Game game, Player seat) {
        return PromiseBook.all(game).stream()
                .filter(promise -> PromisedVotes.onThisAgenda(game, promise))
                .filter(promise -> promise.open() && promise.isPromiser(seat))
                .filter(promise -> promise.proposer().equals(seat.getFaction()))
                .map(Promise::target)
                .findFirst();
    }

    private static boolean votesCommittedElsewhere(AiTurnContext context) {
        return AgendaQueue.holdsQueuedAfter(context.game(), context.seat())
                || AgendaCardRules.holdsUnplacedRider(context)
                || AgendaVoting.abstained(context);
    }

    private static Optional<Player> proposerOf(Game game, Promise promise) {
        return Optional.ofNullable(game.getPlayerFromColorOrFaction(promise.proposer()));
    }

    private static Function<Player, List<TacticalPlan>> rankedOnce(Game game) {
        Map<String, List<TacticalPlan>> ranked = new HashMap<>();
        return player -> ranked.computeIfAbsent(player.getFaction(), faction -> TacticalPlanner.ranked(game, player));
    }

    private static double restraintCost(
            Game game, Function<Player, List<TacticalPlan>> plans, Player promiser, Promise promise) {
        return PromiseValue.restraintCost(game, plans.apply(promiser), promiser.getTacticalCC(), promise);
    }

    private static double restraint(Game game, Player promiser, Promise promise) {
        if (promise.term() != Promise.Term.VOTE_FOR) return PromiseValue.restraintCost(game, promiser, promise);
        Player promisee = promise.promiseeIn(game).orElseThrow();
        return VoteDeals.valuedAt(game, promiser, promiser, promisee, promise.target(), promise.votes())
                .map(VoteDeals.Terms::sellerCost)
                .orElse(Double.POSITIVE_INFINITY);
    }

    private static double protection(Game game, Player promisee, Player promiser, Promise promise) {
        if (promise.term() == Promise.Term.NOT_AGAINST) {
            return RiderExtortion.toHolder(game, promisee, promiser, promise.target())
                    .map(RiderExtortion.Stake::holderGain)
                    .orElse(0.0);
        }
        if (promise.term() != Promise.Term.VOTE_FOR) {
            return PromiseValue.protection(game, promisee, promiser, promise.term(), promise.target());
        }
        return VoteDeals.valuedAt(game, promisee, promiser, promisee, promise.target(), promise.votes())
                .map(VoteDeals.Terms::buyerGain)
                .orElse(0.0);
    }

    private static List<Proposal> territorial(
            AiTurnContext context, Function<Player, List<TacticalPlan>> plans, Player partner) {
        Game game = context.game();
        Player seat = context.seat();
        List<Proposal> proposals = new ArrayList<>();
        if (Extortion.threat(game, seat, partner).isEmpty()) {
            sale(context, plans, partner, Promise.Term.NO_ATTACK, "").ifPresent(proposals::add);
            PromiseValue.mostExposed(game, partner, seat)
                    .flatMap(tile -> sale(context, plans, partner, Promise.Term.STAY_OUT, tile.getPosition()))
                    .ifPresent(proposals::add);
        }
        purchase(context, plans, partner, Promise.Term.NO_ATTACK, "").ifPresent(proposals::add);
        PromiseValue.mostExposed(game, seat, partner)
                .flatMap(tile -> purchase(context, plans, partner, Promise.Term.STAY_OUT, tile.getPosition()))
                .ifPresent(proposals::add);
        return proposals;
    }

    private static Optional<Proposal> sale(
            AiTurnContext context,
            Function<Player, List<TacticalPlan>> plans,
            Player buyer,
            Promise.Term term,
            String target) {
        Game game = context.game();
        Player seat = context.seat();
        double value = PromiseValue.protection(game, buyer, seat, term, target);
        if (value <= 0) return Optional.empty();
        double cost = restraintCost(game, plans, seat, hypothetical(game, term, seat, buyer, target));
        return sellerPrice(value, cost, buyer, buyer.getTg(), MAX_TERRITORY_PRICE)
                .map(price -> new Proposal(term, seat, buyer, target, 0, price, price - cost));
    }

    private static Optional<Proposal> purchase(
            AiTurnContext context,
            Function<Player, List<TacticalPlan>> plans,
            Player seller,
            Promise.Term term,
            String target) {
        Game game = context.game();
        Player seat = context.seat();
        double worth = PromiseValue.protection(game, seat, seller, term, target);
        double value = Trust.of(context, seller) * worth;
        if (value <= 0) return Optional.empty();
        double cost = restraintCost(game, plans, seller, hypothetical(game, term, seller, seat, target));
        int ask = askOf(seller, worth, cost, seat, MAX_TERRITORY_PRICE);
        return buyerPrice(value, cost, seller, spendableTradeGoods(game, seat), MAX_TERRITORY_PRICE, ask)
                .map(price -> new Proposal(term, seller, seat, target, 0, price, value - price));
    }

    private static List<Proposal> votes(
            AiTurnContext context,
            VoteDeals.Outlook outlook,
            boolean mayPromiseVotes,
            Predicate<String> backed,
            Player partner) {
        Game game = context.game();
        Player seat = context.seat();
        boolean conditional = conditional(seat);
        List<Proposal> proposals = new ArrayList<>();
        if (mayPromiseVotes && !sellingVotes(game, seat) && (!conditional || VoteDeals.aboutToVote(game, seat))) {
            VoteDeals.bestSale(outlook, seat, partner, backed)
                    .flatMap(terms -> {
                        double chance = payChance(conditional, terms);
                        int cap = predictionCap(game, seat, partner, terms.outcome());
                        return sellerPrice(terms.buyerGain(), terms.sellerCost(), partner, partner.getTg(), cap, chance)
                                .map(price ->
                                        marketPrices(seat) ? marketAsk(game, terms.sellerCost(), chance, price) : price)
                                .filter(price -> !overPredictionCap(game, seat, partner, terms.outcome(), price, ""))
                                .map(price -> new Proposal(
                                        Promise.Term.VOTE_FOR,
                                        seat,
                                        partner,
                                        terms.outcome(),
                                        terms.votes(),
                                        price,
                                        chance * price - terms.sellerCost()));
                    })
                    .ifPresent(proposals::add);
        }
        if (!sellingVotes(game, partner) && (!conditional || VoteDeals.aboutToVote(game, partner))) {
            double trust = Trust.of(context, partner);
            Optional<Proposal> purchase = VoteDeals.bestPurchase(outlook, seat, partner, backed)
                    .flatMap(terms -> purchase(
                            seat, partner, terms, trust * terms.buyerGain(), payChance(conditional, terms), game));
            if (purchase.isEmpty() && conditional) purchase = blocPurchase(context, outlook, partner, backed, trust);
            purchase.ifPresent(proposals::add);
        }
        if (mayPromiseVotes && !sellingVotes(game, seat)) {
            RiderExtortion.offer(game, seat, partner, backed).ifPresent(proposals::add);
        }
        return proposals;
    }

    private static Optional<Proposal> purchase(
            Player buyer, Player seller, VoteDeals.Terms terms, double value, double chance, Game game) {
        int ask = voteAsk(game, seller, buyer, terms, chance);
        int cap = predictionCap(game, buyer, terms.outcome());
        return buyerPrice(value, terms.sellerCost(), seller, spendableTradeGoods(game, buyer), cap, ask, chance)
                .filter(price -> !overPredictionCap(game, buyer, buyer, terms.outcome(), price, ""))
                .map(price -> new Proposal(
                        Promise.Term.VOTE_FOR,
                        seller,
                        buyer,
                        terms.outcome(),
                        terms.votes(),
                        price,
                        value - chance * price));
    }

    private static Optional<Proposal> blocPurchase(
            AiTurnContext context, VoteDeals.Outlook outlook, Player seller, Predicate<String> backed, double trust) {
        Game game = context.game();
        Player seat = context.seat();
        List<Player> later = VoteDeals.votingAfter(game, seller).stream()
                .filter(partner -> partner != seat)
                .filter(partner -> approachable(context, partner))
                .filter(partner -> noVoteDealYet(game, seat, partner))
                .filter(partner -> !sellingVotes(game, partner))
                .toList();
        return VoteDeals.blocs(outlook, seat, seller, later, backed).stream()
                .flatMap(bloc -> blocProposal(context, bloc, trust).stream())
                .max(Comparator.comparingDouble(Proposal::gain));
    }

    private static Optional<Proposal> blocProposal(AiTurnContext context, VoteDeals.Bloc bloc, double firstTrust) {
        Player seat = context.seat();
        double chance = bloc.chance();
        if (chance < MIN_PAY_CHANCE) return Optional.empty();
        int total = 0;
        List<Integer> prices = new ArrayList<>();
        for (VoteDeals.Member member : bloc.members()) {
            VoteDeals.Terms terms = member.terms();
            int ask = voteAsk(context.game(), member.seller(), seat, terms, chance);
            int price = Math.max(ask, floorPrice(terms.sellerCost(), member.seller(), chance));
            prices.add(price);
            total += price;
        }
        if (overPredictionCap(context.game(), seat, seat, bloc.outcome(), total, "")) return Optional.empty();
        double trust = bloc.members().stream()
                .mapToDouble(member -> Trust.of(context, member.seller()))
                .min()
                .orElse(firstTrust);
        double value = trust * bloc.buyerGain() - chance * total;
        if (total > spendableTradeGoods(context.game(), seat) || value < TradeValue.PROPOSE_MIN)
            return Optional.empty();
        VoteDeals.Member first = bloc.first();
        return Optional.of(new Proposal(
                Promise.Term.VOTE_FOR,
                first.seller(),
                seat,
                bloc.outcome(),
                first.terms().votes(),
                prices.getFirst(),
                value));
    }

    static Optional<Integer> sellerPrice(double value, double cost, Player buyer, int buyerTradeGoods, int cap) {
        return sellerPrice(value, cost, buyer, buyerTradeGoods, cap, 1);
    }

    static Optional<Integer> sellerPrice(
            double value, double cost, Player buyer, int buyerTradeGoods, int cap, double payChance) {
        if (payChance < MIN_PAY_CHANCE) return Optional.empty();
        int price = (int) Math.floor((value - partnerMinimum(buyer)) / payChance);
        price = Math.min(price, buyerTradeGoods);
        if (cap > 0) price = Math.min(price, cap);
        if (price < MIN_PRICE || payChance * price - cost < TradeValue.PROPOSE_MIN) return Optional.empty();
        return Optional.of(price);
    }

    static Optional<Integer> buyerPrice(double value, double cost, Player seller, int ownTradeGoods, int cap) {
        return buyerPrice(value, cost, seller, ownTradeGoods, cap, 0, 1);
    }

    static Optional<Integer> buyerPrice(
            double value, double cost, Player seller, int ownTradeGoods, int cap, int sellerAsk) {
        return buyerPrice(value, cost, seller, ownTradeGoods, cap, sellerAsk, 1);
    }

    static Optional<Integer> buyerPrice(
            double value, double cost, Player seller, int ownTradeGoods, int cap, int sellerAsk, double payChance) {
        if (payChance < MIN_PAY_CHANCE) return Optional.empty();
        int price = Math.max(sellerAsk, floorPrice(cost, seller, payChance));
        if (price > ownTradeGoods || (cap > 0 && price > cap)) return Optional.empty();
        if (value - payChance * price < TradeValue.PROPOSE_MIN) return Optional.empty();
        return Optional.of(price);
    }

    private static int floorPrice(double cost, Player seller, double payChance) {
        return Math.max(MIN_PRICE, (int) Math.ceil((cost + partnerMinimum(seller)) / payChance));
    }

    private static int askOf(Player seller, double worth, double cost, Player buyer, int cap) {
        return askOf(seller, worth, cost, buyer, cap, 1);
    }

    private static int askOf(Player seller, double worth, double cost, Player buyer, int cap, double payChance) {
        if (!AiSeats.isAiSeat(seller)) return 0;
        return sellerPrice(worth, cost, buyer, buyer.getTg(), cap, payChance).orElse(0);
    }

    static double partnerMinimum(Player partner) {
        return AiSeats.isAiSeat(partner) ? TradeValue.PREDICTED_MIN : TradeValue.HUMAN_GAIN_MIN;
    }

    private static Promise hypothetical(Game game, Promise.Term term, Player promiser, Player promisee, String target) {
        return Promise.offer(game, term, promiser, promisee, promiser, "", target, 0, 0, 0, 0);
    }

    static boolean approachableForVotes(AiTurnContext context, Player partner) {
        return approachable(context, partner) && noVoteDealYet(context.game(), context.seat(), partner);
    }

    static Optional<AiDecision> postProposal(AiTurnContext context, Proposal proposal) {
        return post(context, proposal);
    }

    private static boolean approachable(AiTurnContext context, Player partner) {
        Game game = context.game();
        if (ScoringStakes.contender(game, context.seat(), partner)) return false;
        return AiSeats.isAiSeat(partner) ? active(partner) : OwnTurnDeals.mayApproach(context, partner);
    }

    private static long proposedThisScope(Game game, Player seat, boolean votes) {
        return PromiseBook.proposedBy(game, seat).stream()
                .filter(promise -> votes ? PromisedVotes.onThisAgenda(game, promise) : promise.territorial())
                .count();
    }

    private static boolean noTerritorialDealYet(Game game, Player seat, Player partner) {
        return PromiseBook.all(game).stream()
                .filter(promise -> promise.thisRound(game) && promise.territorial())
                .noneMatch(promise -> PromiseBook.between(promise, seat, partner));
    }

    static boolean noVoteDealYet(Game game, Player seat, Player partner) {
        return PromiseBook.all(game).stream()
                .filter(promise -> PromisedVotes.onThisAgenda(game, promise))
                .noneMatch(promise -> PromiseBook.between(promise, seat, partner));
    }

    private static boolean sellingVotes(Game game, Player seller) {
        return PromiseBook.all(game).stream()
                .filter(promise -> PromisedVotes.onThisAgenda(game, promise))
                .filter(promise -> promise.open() || promise.status() == Promise.Status.ACCEPTED)
                .anyMatch(promise -> promise.isPromiser(seller));
    }

    private static Optional<AiDecision> post(AiTurnContext context, Proposal proposal) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<String> channel = channelId(context);
        if (channel.isEmpty()) return Optional.empty();
        Player partner = proposal.promiser() == seat ? proposal.promisee() : proposal.promiser();
        String agenda = proposal.term() == Promise.Term.VOTE_FOR || proposal.term() == Promise.Term.NOT_AGAINST
                ? PromisedVotes.agendaKey(game)
                : "";
        Promise promise = Promise.offer(
                game,
                proposal.term(),
                proposal.promiser(),
                proposal.promisee(),
                seat,
                agenda,
                proposal.target(),
                proposal.votes(),
                proposal.price(),
                context.now(),
                context.now() + TradeRules.ownTurnReplyMillis(partner));
        PromisePayment.attach(game, promise.id(), proposal.payment());
        PromiseBook.persist(game, promise);
        if (!paysOnResolution(game, promise)) TradeLegality.useWindow(context, partner);
        return Optional.of(new AiDecision.Post(
                channel.get(), offerText(game, seat, partner, promise), offerButtons(game, partner, promise)));
    }

    static Optional<String> channelId(AiTurnContext context) {
        Optional<String> table = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.source() == AiPrompt.PromptSource.PUBLIC)
                .map(AiPrompt::channelId)
                .findFirst();
        if (table.isPresent()) return table;
        TextChannel channel = context.seat().getCorrectChannel();
        return channel == null ? Optional.empty() : Optional.of(channel.getId());
    }

    static String offerText(Game game, Player proposer, Player partner, Promise promise) {
        String name = proposer.getFactionEmojiOrColor();
        String price = PromisePayment.of(game, promise).describe();
        String deal = promise.isPromiser(proposer)
                ? name + " offers to " + describe(game, promise, true) + " if you pay it " + price + "."
                : name + " offers you " + price + " to " + describe(game, promise, false) + ".";
        return partner.getRepresentationUnfogged() + " " + deal + " " + paymentTerms(game, promise);
    }

    private static String paymentTerms(Game game, Promise promise) {
        if (!paysOnResolution(game, promise)) return "The payment changes hands when you accept.";
        return "Nothing is paid now: the payment changes hands after the agenda, and only if it resolves for **"
                + outcomeName(game, promise.target()) + "**.";
    }

    private static List<Button> offerButtons(Game game, Player partner, Promise promise) {
        String owner = ComponentIdEnvelope.ownedBy(partner.getFaction());
        return List.of(
                Buttons.green(
                        owner + ACCEPT_PREFIX + promise.id(),
                        "Accept (" + PromisePayment.of(game, promise).label() + ")"),
                Buttons.gray(owner + DECLINE_PREFIX + promise.id(), "Decline"));
    }

    static String describe(Game game, Promise promise, boolean promiserSpeaks) {
        return switch (promise.term()) {
            case VOTE_FOR ->
                "cast " + (promiserSpeaks ? "its" : "your") + " votes for **" + outcomeName(game, promise.target())
                        + "** on this agenda";
            case NOT_AGAINST ->
                "not vote against **" + outcomeName(game, promise.target()) + "** on this agenda ("
                        + (promiserSpeaks ? "it abstains or votes" : "you abstain or vote") + " for it)";
            case NO_ATTACK ->
                "not attack " + (promiserSpeaks ? "your" : "its") + " units or planets this round (no combat or"
                        + " invasion against them, and no activation of a system that holds them)";
            case STAY_OUT -> "not move ships into " + tileName(game, promise.target()) + " this round";
            case DISCARD_AGENDA -> "discard this agenda with " + promise.target();
        };
    }

    private static String outcomeName(Game game, String outcome) {
        Player player = game.getPlayerFromColorOrFaction(outcome);
        return player == null ? outcome : player.getFactionEmojiOrColor();
    }

    private static String tileName(Game game, String position) {
        Tile tile = game.getTileByPosition(position);
        return tile == null ? position : tile.getRepresentationForButtons();
    }
}
