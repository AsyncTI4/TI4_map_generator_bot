package ti4.ai.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import ti4.ai.agenda.AgendaVoting;
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
    private static final int MIN_PRICE = 1;
    private static final Comparator<Proposal> BEST = Comparator.comparingDouble(Proposal::gain)
            .thenComparing(proposal -> proposal.term() == Promise.Term.STAY_OUT);

    record Proposal(
            Promise.Term term, Player promiser, Player promisee, String target, int votes, int price, double gain) {}

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
        if (proposedThisScope(game, seat, true) >= MAX_PER_SCOPE) return Optional.empty();
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
                .max(Comparator.comparingDouble(Proposal::gain))
                .flatMap(proposal -> post(context, proposal));
    }

    static Optional<AiDecision> answer(AiTurnContext context) {
        Player seat = context.seat();
        if (!active(seat)) return Optional.empty();
        Game game = context.game();
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
                    proposerOf(game, promise.get()).ifPresent(proposer -> TradeLegality.useWindow(context, proposer));
                    return Optional.of(AiDecision.press(prompt, button, "accept the promise deal"));
                }
                Optional<PromptButton> decline = prompt.firstEnabled(candidate -> candidate.isOwnedBy(context.faction())
                        && candidate.handlerId().equals(DECLINE_PREFIX + id));
                if (decline.isPresent() && !context.alreadyPressed(prompt, decline.get())) {
                    return Optional.of(AiDecision.press(prompt, decline.get(), "decline the promise deal"));
                }
            }
        }
        return Optional.empty();
    }

    static void observe(AiTurnContext context) {
        Player seat = context.seat();
        if (!active(seat)) return;
        Game game = context.game();
        for (Promise promise : PromiseBook.all(game)) {
            Optional<Promise.Status> status = settled(context, promise);
            if (status.isPresent()) {
                PromiseBook.persist(game, promise.with(status.get()));
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
        if (promise.status() != Promise.Status.ACCEPTED) return Optional.empty();
        if (!promise.isPromisee(seat) && !promise.isPromiser(seat)) return Optional.empty();
        return promise.term() == Promise.Term.VOTE_FOR
                ? PromisedVotes.verdict(game, promise)
                : PromiseChecks.territorial(game, promise);
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
        if (promise.term() == Promise.Term.VOTE_FOR) {
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
        if (!TradeLegality.canTransact(game, seat, proposer) || TradeLegality.windowUsed(context, proposer)) {
            return false;
        }
        double margin = TradeValue.margin(proposer);
        if (promise.isPromiser(seat)) return sells(context, promise, promisee, margin);
        double gain = Trust.of(context, promiser) * protection(game, seat, promiser, promise);
        return seat.getTg() >= promise.price() && gain - promise.price() >= margin;
    }

    private static boolean sells(AiTurnContext context, Promise promise, Player buyer, double margin) {
        Game game = context.game();
        Player seat = context.seat();
        boolean votes = promise.term() == Promise.Term.VOTE_FOR;
        if (votes
                && (votesCommittedElsewhere(context) || !backedOutcome(context).test(promise.target()))) {
            return false;
        }
        double cost = restraint(game, seat, promise);
        double worth = votes
                ? VoteDeals.valuedAt(game, seat, seat, buyer, promise.target(), promise.votes())
                        .map(VoteDeals.Terms::buyerGain)
                        .orElse(0.0)
                : PromiseValue.protection(game, buyer, seat, promise.term(), promise.target());
        Optional<Integer> asked = sellerPrice(worth, cost, buyer, buyer.getTg(), votes ? 0 : MAX_TERRITORY_PRICE);
        int ask = votes ? Math.max(asked.orElse(0), voteFloor(cost)) : asked.orElse(0);
        return promise.price() >= ask && promise.price() - cost >= margin;
    }

    static int voteFloor(double sellerCost) {
        return Math.max(MIN_PRICE, (int) Math.ceil(sellerCost + TradeValue.PROPOSE_MIN));
    }

    static Predicate<String> backedOutcome(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<String> backed = AgendaVoting.ballotInProgress(context)
                .or(() -> PromisedVotes.binding(game, seat).map(Promise::target))
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
                || AgendaCardRules.holdsUnplacedRider(context);
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
        return buyerPrice(value, cost, seller, seat.getTg(), MAX_TERRITORY_PRICE, ask)
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
        List<Proposal> proposals = new ArrayList<>();
        if (mayPromiseVotes && !sellingVotes(game, seat)) {
            VoteDeals.bestSale(outlook, seat, partner, backed)
                    .flatMap(terms -> sellerPrice(terms.buyerGain(), terms.sellerCost(), partner, partner.getTg(), 0)
                            .map(price -> new Proposal(
                                    Promise.Term.VOTE_FOR,
                                    seat,
                                    partner,
                                    terms.outcome(),
                                    terms.votes(),
                                    price,
                                    price - terms.sellerCost())))
                    .ifPresent(proposals::add);
        }
        if (!sellingVotes(game, partner)) {
            double trust = Trust.of(context, partner);
            VoteDeals.bestPurchase(outlook, seat, partner, backed)
                    .flatMap(terms -> buyerPrice(
                                    trust * terms.buyerGain(),
                                    terms.sellerCost(),
                                    partner,
                                    seat.getTg(),
                                    0,
                                    askOf(partner, terms.buyerGain(), terms.sellerCost(), seat, 0))
                            .map(price -> new Proposal(
                                    Promise.Term.VOTE_FOR,
                                    partner,
                                    seat,
                                    terms.outcome(),
                                    terms.votes(),
                                    price,
                                    trust * terms.buyerGain() - price)))
                    .ifPresent(proposals::add);
        }
        return proposals;
    }

    static Optional<Integer> sellerPrice(double value, double cost, Player buyer, int buyerTradeGoods, int cap) {
        int price = (int) Math.floor(value - partnerMinimum(buyer));
        price = Math.min(price, buyerTradeGoods);
        if (cap > 0) price = Math.min(price, cap);
        if (price < MIN_PRICE || price - cost < TradeValue.PROPOSE_MIN) return Optional.empty();
        return Optional.of(price);
    }

    static Optional<Integer> buyerPrice(double value, double cost, Player seller, int ownTradeGoods, int cap) {
        return buyerPrice(value, cost, seller, ownTradeGoods, cap, 0);
    }

    static Optional<Integer> buyerPrice(
            double value, double cost, Player seller, int ownTradeGoods, int cap, int sellerAsk) {
        int price = Math.max(sellerAsk, Math.max(MIN_PRICE, (int) Math.ceil(cost + partnerMinimum(seller))));
        if (price > ownTradeGoods || (cap > 0 && price > cap)) return Optional.empty();
        if (value - price < TradeValue.PROPOSE_MIN) return Optional.empty();
        return Optional.of(price);
    }

    private static int askOf(Player seller, double worth, double cost, Player buyer, int cap) {
        if (!AiSeats.isAiSeat(seller)) return 0;
        return sellerPrice(worth, cost, buyer, buyer.getTg(), cap).orElse(0);
    }

    private static double partnerMinimum(Player partner) {
        return AiSeats.isAiSeat(partner) ? TradeValue.PREDICTED_MIN : TradeValue.HUMAN_GAIN_MIN;
    }

    private static Promise hypothetical(Game game, Promise.Term term, Player promiser, Player promisee, String target) {
        return Promise.offer(game, term, promiser, promisee, promiser, "", target, 0, 0, 0, 0);
    }

    private static boolean approachable(AiTurnContext context, Player partner) {
        Game game = context.game();
        if (ScoringStakes.contender(game, context.seat(), partner)) return false;
        return AiSeats.isAiSeat(partner) ? active(partner) : OwnTurnDeals.mayApproach(context, partner);
    }

    private static long proposedThisScope(Game game, Player seat, boolean votes) {
        return PromiseBook.proposedBy(game, seat).stream()
                .filter(promise ->
                        votes ? PromisedVotes.onThisAgenda(game, promise) : promise.term() != Promise.Term.VOTE_FOR)
                .count();
    }

    private static boolean noTerritorialDealYet(Game game, Player seat, Player partner) {
        return PromiseBook.all(game).stream()
                .filter(promise -> promise.thisRound(game) && promise.term() != Promise.Term.VOTE_FOR)
                .noneMatch(promise -> PromiseBook.between(promise, seat, partner));
    }

    private static boolean noVoteDealYet(Game game, Player seat, Player partner) {
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
        String agenda = proposal.term() == Promise.Term.VOTE_FOR ? PromisedVotes.agendaKey(game) : "";
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
        PromiseBook.persist(game, promise);
        TradeLegality.useWindow(context, partner);
        return Optional.of(new AiDecision.Post(
                channel.get(), offerText(game, seat, partner, promise), offerButtons(partner, promise)));
    }

    private static Optional<String> channelId(AiTurnContext context) {
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
        String price = promise.price() + " trade goods";
        String deal = promise.isPromiser(proposer)
                ? name + " offers to " + describe(game, promise, true) + " if you pay it " + price + "."
                : name + " offers you " + price + " to " + describe(game, promise, false) + ".";
        return partner.getRepresentationUnfogged() + " " + deal + " The trade goods change hands when you accept.";
    }

    private static List<Button> offerButtons(Player partner, Promise promise) {
        String owner = ComponentIdEnvelope.ownedBy(partner.getFaction());
        return List.of(
                Buttons.green(owner + ACCEPT_PREFIX + promise.id(), "Accept (" + promise.price() + " TG)"),
                Buttons.gray(owner + DECLINE_PREFIX + promise.id(), "Decline"));
    }

    static String describe(Game game, Promise promise, boolean promiserSpeaks) {
        return switch (promise.term()) {
            case VOTE_FOR ->
                "cast " + (promiserSpeaks ? "its" : "your") + " votes for **" + outcomeName(game, promise.target())
                        + "** on this agenda";
            case NO_ATTACK ->
                "not attack " + (promiserSpeaks ? "your" : "its") + " units or planets this round (no combat or"
                        + " invasion against them, and no activation of a system that holds them)";
            case STAY_OUT -> "not move ships into " + tileName(game, promise.target()) + " this round";
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
