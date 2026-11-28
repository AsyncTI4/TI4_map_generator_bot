package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static ti4.ai.AiTestGame.NOW;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.agenda.AgendaVoting;
import ti4.ai.agenda.GalacticThreatCost;
import ti4.ai.agenda.PromisedVotes;
import ti4.ai.agenda.VoteDeals;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.ai.tactical.TacticalPlan;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// Promise deals: a seat sells or buys a promise ("no attack on you this round", "no ships into that system", "my
// votes for that outcome") for trade goods. Prices sit between what the promise costs the promiser and what it is
// worth to the promisee, inside the usual deal margins. The promise itself is a non-binding deal (LRR 28.4, and a vote
// promise before the voter's turn is note II.δ), so only the trade goods are a transaction: in the action phase it
// must be on the active player's turn (LRR 94.1); in the agenda phase any two players may transact.
class PromiseDealsTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;
    private static final String THIRD_AI_ID = "7100000555555555";
    private static final String SHARD = "Law_Elect Player_7_shard_of_the_throne";

    private static AiPrompt table() {
        return AiTestGame.prompt("turn", PromptSource.PUBLIC, NOW - 500, "FFCC_nekro_done");
    }

    private static AiPrompt offerPrompt(Player partner, Promise promise) {
        String owner = "FFCC_" + partner.getFaction() + "_";
        return AiTestGame.prompt(
                "offer",
                PromptSource.PUBLIC,
                NOW,
                owner + PromiseDeals.ACCEPT_PREFIX + promise.id(),
                owner + PromiseDeals.DECLINE_PREFIX + promise.id());
    }

    // Sol's home sits next to a system with two Nekro dreadnoughts, which outgun the empty home system.
    private static Tile threatenSolHome(AiTestGame test) {
        test.nekroHome();
        Tile lodor = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        Tile jord = test.solHomeBeside(lodor.getPosition());
        test.units(lodor, "space", test.nekro, UnitType.Dreadnought, 2);
        test.game.setVp(10);
        test.game.setLastModifiedDate(7);
        return jord;
    }

    // Sol holds Lodor, next to Nekro's home, so the two are neighbours and may transact in the action phase (LRR 94.1).
    private static Tile solNextDoor(AiTestGame test) {
        Tile lodor = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.sol.addPlanet("lodor");
        return lodor;
    }

    private static Promise offer(
            AiTestGame test, Promise.Term term, Player promiser, Player promisee, Player proposer, int price) {
        return Promise.offer(test.game, term, promiser, promisee, proposer, "", "", 0, price, NOW, NOW + 60_000);
    }

    // The seller takes as much as the buyer still gains from (worth less the buyer's margin), within its trade goods
    // and the 5 trade good cap of an extortion demand; a sale that does not cover the seller's cost plus its own
    // proposing margin is not offered.
    @Test
    void aSellerAsksWhatTheBuyerStillGainsFrom() {
        AiTestGame test = new AiTestGame();

        assertThat(PromiseDeals.sellerPrice(6.0, 0, test.sol, 10, 5)).contains(5);
        assertThat(PromiseDeals.sellerPrice(6.0, 0, test.sol, 2, 5)).contains(2);
        assertThat(PromiseDeals.sellerPrice(4.0, 0, test.sol, 10, 5)).contains(3);
        assertThat(PromiseDeals.sellerPrice(6.0, 4.8, test.sol, 10, 5)).isEmpty();
        assertThat(PromiseDeals.sellerPrice(1.2, 0, test.sol, 10, 5)).isEmpty();
    }

    // A vote sale never goes below the seller's cost plus its proposing margin: votes sell for 1 trade good only when
    // the seller does not care how the agenda goes.
    @Test
    void votesNeverSellBelowTheSellersCost() {
        assertThat(PromiseDeals.voteFloor(0)).isEqualTo(1);
        assertThat(PromiseDeals.voteFloor(0.4)).isEqualTo(1);
        assertThat(PromiseDeals.voteFloor(0.6)).isEqualTo(2);
        assertThat(PromiseDeals.voteFloor(3.2)).isEqualTo(4);
    }

    // The buyer offers the least the seller would take (its cost plus the seller's margin, at least 1 trade good) and
    // only when it keeps at least its proposing margin.
    @Test
    void aBuyerOffersTheLeastTheSellerTakes() {
        AiTestGame test = AiTestGame.withSolAi();

        assertThat(PromiseDeals.buyerPrice(6.0, 0, test.sol, 10, 5)).contains(1);
        assertThat(PromiseDeals.buyerPrice(6.0, 1.8, test.sol, 10, 5)).contains(3);
        assertThat(PromiseDeals.buyerPrice(6.0, 1.8, test.sol, 2, 5)).isEmpty();
        assertThat(PromiseDeals.buyerPrice(1.2, 0, test.sol, 10, 5)).isEmpty();
        assertThat(PromiseDeals.buyerPrice(20.0, 6, test.sol, 10, 5)).isEmpty();
    }

    // Nekro's dreadnoughts outgun Sol's empty home, so Sol fears losing it, while giving up the attack costs Nekro less
    // than that fear is worth. Nekro sells Sol a promise for the 3 trade goods Sol has.
    @Test
    void sellsPeaceForWhatTheBuyerFears() {
        AiTestGame test = new AiTestGame();
        Tile jord = threatenSolHome(test);
        test.sol.setTg(3);
        test.aiIsActive("action");

        AiTurnContext context = test.context(table());
        Optional<AiDecision> decision = PromiseDeals.proposeOwnTurn(context, List.of(test.sol));

        AiDecision.Post post = (AiDecision.Post) decision.orElseThrow();
        // The offer is this turn's transaction with Sol (LRR 94.1): no wash or other deal with Sol follows it.
        assertThat(TradeLegality.windowUsed(context, test.sol)).isTrue();
        assertThat(PromiseDeals.offerOpenWith(test.game, test.nekro, test.sol)).isTrue();
        Promise promise = PromiseBook.proposedBy(test.game, test.nekro).getFirst();
        assertThat(promise.status()).isEqualTo(Promise.Status.OFFERED);
        assertThat(promise.isPromiser(test.nekro)).isTrue();
        assertThat(promise.price()).isEqualTo(3);
        assertThat(post.buttons())
                .extracting(button -> button.getCustomId())
                .containsExactly(
                        "FFCC_sol_" + PromiseDeals.ACCEPT_PREFIX + promise.id(),
                        "FFCC_sol_" + PromiseDeals.DECLINE_PREFIX + promise.id());
        assertThat(post.text()).contains("3 trade goods");
        // The offer binds Nekro while it stands: Sol's home is off limits.
        assertThat(PromiseBook.forbidsActivating(test.game, test.nekro, jord)).isTrue();
    }

    @Test
    void aBuyerThatCannotPayIsOfferedNothing() {
        AiTestGame test = new AiTestGame();
        threatenSolHome(test);
        test.sol.setTg(0);
        test.aiIsActive("action");

        assertThat(PromiseDeals.proposeOwnTurn(test.context(table()), List.of(test.sol)))
                .isEmpty();
    }

    @Test
    void controlSeatOffersNoPromises() {
        AiTestGame test = new AiTestGame();
        threatenSolHome(test);
        test.sol.setTg(3);
        test.aiIsActive("action");
        AiExperiments.disable(test.nekro, Set.of(Experiment.PROMISE_DEALS));

        assertThat(PromiseDeals.proposeOwnTurn(test.context(table()), List.of(test.sol)))
                .isEmpty();
        assertThat(PromiseBook.all(test.game)).isEmpty();
    }

    // Sol (an AI with no ships at all) is asked to promise not to attack Nekro for 2 trade goods: the promise costs it
    // nothing, so it accepts.
    @Test
    void anAiAcceptsMoneyForAPromiseThatCostsItNothing() {
        AiTestGame test = AiTestGame.withSolAi();
        test.nekroHome();
        solNextDoor(test);
        test.game.setVp(10);
        test.aiIsActive("action");
        Promise promise = offer(test, Promise.Term.NO_ATTACK, test.sol, test.nekro, test.nekro, 2);
        PromiseBook.save(test.game, promise);

        AiTurnContext context = test.contextFor(test.sol, Set.of(), NOW, offerPrompt(test.sol, promise));

        assertThat(AiTestGame.pressedId(PromiseDeals.answer(context).orElseThrow()))
                .isEqualTo("FFCC_sol_" + PromiseDeals.ACCEPT_PREFIX + promise.id());
        // Accepting is Sol's one transaction with Nekro this turn (LRR 94.1), so a second offer is turned down.
        assertThat(TradeLegality.windowUsed(context, test.nekro)).isTrue();
        Promise second = Promise.offer(
                test.game,
                Promise.Term.STAY_OUT,
                test.sol,
                test.nekro,
                test.nekro,
                "",
                "x",
                0,
                2,
                NOW + 1,
                NOW + 60_000);
        PromiseBook.save(test.game, second);
        AiTurnContext later = test.contextFor(test.sol, Set.of(), NOW, offerPrompt(test.sol, second));
        assertThat(AiTestGame.pressedId(PromiseDeals.answer(later).orElseThrow()))
                .isEqualTo("FFCC_sol_" + PromiseDeals.DECLINE_PREFIX + second.id());
    }

    // Strangers cannot transact in the action phase (LRR 94.1), so an AI turns down money from a non-neighbour.
    @Test
    void anAiTurnsDownAPromiseDealWithAStranger() {
        AiTestGame test = AiTestGame.withSolAi();
        test.nekroHome();
        test.game.setVp(10);
        test.aiIsActive("action");
        Promise promise = offer(test, Promise.Term.NO_ATTACK, test.sol, test.nekro, test.nekro, 2);
        PromiseBook.save(test.game, promise);

        AiTurnContext context = test.contextFor(test.sol, Set.of(), NOW, offerPrompt(test.sol, promise));

        assertThat(AiTestGame.pressedId(PromiseDeals.answer(context).orElseThrow()))
                .isEqualTo("FFCC_sol_" + PromiseDeals.DECLINE_PREFIX + promise.id());
    }

    // The promise lasts the round. Nekro's best move this turn is elsewhere (6), but an attack on Sol (4) is its
    // second-best: with two tactical tokens left it would make that attack on a later turn, so the promise costs it the
    // whole attack. With a single token the attack would never come, and the promise costs it nothing.
    @Test
    void aSecondBestAttackOnTheBuyerIsPricedIn() {
        AiTestGame test = new AiTestGame();
        Tile elsewhere = test.place("19", "310");
        Tile solSide = test.place("26", "304");
        Promise stayOut = offer(test, Promise.Term.STAY_OUT, test.nekro, test.sol, test.sol, 1);
        Promise promise = new Promise(
                stayOut.id(),
                stayOut.term(),
                stayOut.promiser(),
                stayOut.promisee(),
                stayOut.proposer(),
                stayOut.round(),
                stayOut.agenda(),
                solSide.getPosition(),
                0,
                1,
                stayOut.status(),
                stayOut.offeredAt(),
                stayOut.expiresAt(),
                List.of(),
                List.of());
        List<TacticalPlan> plans = List.of(
                new TacticalPlan(TacticalPlan.Kind.EXPAND, elsewhere.getPosition(), List.of(), Map.of(), 6.0),
                new TacticalPlan(TacticalPlan.Kind.ATTACK, solSide.getPosition(), List.of(), Map.of(), 4.0));

        assertThat(PromiseValue.restraintCost(test.game, plans, 2, promise)).isCloseTo(4.0, within(EXACT));
        assertThat(PromiseValue.restraintCost(test.game, plans, 1, promise)).isCloseTo(0.0, within(EXACT));
        assertThat(PromiseValue.restraintCost(test.game, plans.reversed(), 1, promise))
                .isCloseTo(0.0, within(EXACT));
        assertThat(PromiseValue.restraintCost(test.game, List.of(plans.get(1)), 1, promise))
                .isCloseTo(4.0, within(EXACT));
    }

    // Sol (an AI) fears the Nekro fleet next to its home and offers Nekro 1 trade good for peace. Nekro asks what the
    // peace is worth to Sol, not just what it costs Nekro, so 1 trade good is too little.
    @Test
    void aSellerTurnsDownLessThanItWouldAsk() {
        AiTestGame test = AiTestGame.withSolAi();
        threatenSolHome(test);
        test.sol.setTg(5);
        test.isActive(test.sol, "action");
        Promise promise = offer(test, Promise.Term.NO_ATTACK, test.nekro, test.sol, test.sol, 1);
        PromiseBook.save(test.game, promise);

        AiTurnContext context = test.contextFor(test.nekro, Set.of(), NOW, offerPrompt(test.nekro, promise));

        assertThat(AiTestGame.pressedId(PromiseDeals.answer(context).orElseThrow()))
                .isEqualTo("FFCC_nekro_" + PromiseDeals.DECLINE_PREFIX + promise.id());
    }

    // When Sol (an AI) itself proposes, it offers what Nekro would ask: enough for Nekro to accept.
    @Test
    void anAiBuyerOffersWhatAnAiSellerAsks() {
        AiTestGame test = AiTestGame.withSolAi();
        threatenSolHome(test);
        test.sol.setTg(5);
        test.isActive(test.sol, "action");

        AiTurnContext context = test.contextFor(test.sol, Set.of(), NOW, table());
        PromiseDeals.proposeOwnTurn(context, List.of(test.nekro)).orElseThrow();
        Promise promise = PromiseBook.proposedBy(test.game, test.sol).getFirst();

        assertThat(promise.isPromiser(test.nekro)).isTrue();
        assertThat(promise.price()).isGreaterThan(1);
        AiTurnContext answer = test.contextFor(test.nekro, Set.of(), NOW, offerPrompt(test.nekro, promise));
        assertThat(AiTestGame.pressedId(PromiseDeals.answer(answer).orElseThrow()))
                .isEqualTo("FFCC_nekro_" + PromiseDeals.ACCEPT_PREFIX + promise.id());
    }

    // A seller that has already activated the buyer's system, or has no tactical tokens left, cannot attack it this
    // round (LRR 5.2), so its peace is worth nothing to the buyer.
    @Test
    void peaceTheSellerCannotBreakIsWorthNothing() {
        AiTestGame test = new AiTestGame();
        Tile jord = threatenSolHome(test);
        assertThat(PromiseValue.protection(test.game, test.sol, test.nekro, Promise.Term.NO_ATTACK, ""))
                .isPositive();

        test.nekro.setTacticalCC(0);
        assertThat(PromiseValue.protection(test.game, test.sol, test.nekro, Promise.Term.NO_ATTACK, ""))
                .isZero();

        test.nekro.setTacticalCC(3);
        jord.addCC(Mapper.getCCID(test.nekro.getColor()));
        assertThat(PromiseValue.protection(test.game, test.sol, test.nekro, Promise.Term.NO_ATTACK, ""))
                .isZero();
    }

    // Nobody sells anything to a player about to win.
    @Test
    void anAiTurnsDownAContender() {
        AiTestGame test = AiTestGame.withSolAi();
        test.nekroHome();
        test.game.setVp(10);
        test.points(test.nekro, 8);
        test.aiIsActive("action");
        Promise promise = offer(test, Promise.Term.NO_ATTACK, test.sol, test.nekro, test.nekro, 2);
        PromiseBook.save(test.game, promise);

        AiTurnContext context = test.contextFor(test.sol, Set.of(), NOW, offerPrompt(test.sol, promise));

        assertThat(AiTestGame.pressedId(PromiseDeals.answer(context).orElseThrow()))
                .isEqualTo("FFCC_sol_" + PromiseDeals.DECLINE_PREFIX + promise.id());
    }

    // Sol (an AI) is under duress from the Nekro fleet next to its home and is offered peace for 1 trade good: three
    // quarters of what it stands to lose is worth far more, so it pays.
    @Test
    void aThreatenedAiBuysPeace() {
        AiTestGame test = AiTestGame.withSolAi();
        threatenSolHome(test);
        test.sol.setTg(3);
        test.aiIsActive("action");
        Promise promise = offer(test, Promise.Term.NO_ATTACK, test.nekro, test.sol, test.nekro, 1);
        PromiseBook.save(test.game, promise);
        assertThat(PromiseValue.protection(test.game, test.sol, test.nekro, Promise.Term.NO_ATTACK, ""))
                .isGreaterThan(1.3);

        AiTurnContext context = test.contextFor(test.sol, Set.of(), NOW, offerPrompt(test.sol, promise));

        assertThat(AiTestGame.pressedId(PromiseDeals.answer(context).orElseThrow()))
                .isEqualTo("FFCC_sol_" + PromiseDeals.ACCEPT_PREFIX + promise.id());
    }

    @Test
    void controlSeatIgnoresOffers() {
        AiTestGame test = AiTestGame.withSolAi();
        test.nekroHome();
        test.aiIsActive("action");
        Promise promise = offer(test, Promise.Term.NO_ATTACK, test.sol, test.nekro, test.nekro, 2);
        PromiseBook.save(test.game, promise);
        AiExperiments.disable(test.sol, Set.of(Experiment.PROMISE_DEALS));

        AiTurnContext context = test.contextFor(test.sol, Set.of(), NOW, offerPrompt(test.sol, promise));

        assertThat(PromiseDeals.answer(context)).isEmpty();
    }

    // A human who took Nekro's trade goods for a NO_ATTACK promise and then invaded its home broke the promise: the
    // book records it and Nekro trusts Sol 0.3 less, as much as for skipping a Trade follow payment.
    @Test
    void aBrokenPromiseLowersTrust() {
        AiTestGame test = new AiTestGame();
        Tile home = test.nekroHome();
        test.aiIsActive("action");
        PromiseBook.save(
                test.game,
                offer(test, Promise.Term.NO_ATTACK, test.sol, test.nekro, test.nekro, 2)
                        .accepted(test.game));
        AiTurnContext context = test.context();
        assertThat(Trust.of(context, test.sol)).isCloseTo(0.8, within(EXACT));

        home.addCC(Mapper.getCCID(test.sol.getColor()));
        test.units(home, "space", test.sol, UnitType.Dreadnought, 1);
        PromiseDeals.observe(context);

        assertThat(PromiseBook.brokenAgainst(test.game, test.nekro)).hasSize(1);
        assertThat(Trust.of(context, test.sol)).isCloseTo(0.5, within(EXACT));
    }

    @Test
    void controlSeatKeepsNoPromiseLedger() {
        AiTestGame test = new AiTestGame();
        Tile home = test.nekroHome();
        test.aiIsActive("action");
        PromiseBook.save(
                test.game,
                offer(test, Promise.Term.NO_ATTACK, test.sol, test.nekro, test.nekro, 2)
                        .accepted(test.game));
        AiExperiments.disable(test.nekro, Set.of(Experiment.PROMISE_DEALS));
        home.addCC(Mapper.getCCID(test.sol.getColor()));
        test.units(home, "space", test.sol, UnitType.Dreadnought, 1);
        AiTurnContext context = test.context();

        PromiseDeals.observe(context);

        assertThat(PromiseBook.brokenAgainst(test.game, test.nekro)).isEmpty();
        assertThat(Trust.of(context, test.sol)).isCloseTo(0.8, within(EXACT));
    }

    @Test
    void anUnansweredOfferExpires() {
        AiTestGame test = new AiTestGame();
        test.aiIsActive("action");
        Promise promise = offer(test, Promise.Term.NO_ATTACK, test.nekro, test.sol, test.nekro, 2);
        PromiseBook.save(test.game, promise);

        PromiseDeals.observe(test.contextAt(NOW + 30_000));
        assertThat(PromiseBook.find(test.game, promise.id()).orElseThrow().status())
                .isEqualTo(Promise.Status.OFFERED);

        PromiseDeals.observe(test.contextAt(NOW + 60_000));
        assertThat(PromiseBook.find(test.game, promise.id()).orElseThrow().status())
                .isEqualTo(Promise.Status.EXPIRED);
    }

    // The trade goods can only change hands on the proposer's turn (LRR 94.1), so an offer lapses when that turn ends.
    @Test
    void aTerritorialOfferLapsesWithTheProposersTurn() {
        AiTestGame test = new AiTestGame();
        test.aiIsActive("action");
        Promise promise = offer(test, Promise.Term.NO_ATTACK, test.nekro, test.sol, test.nekro, 2);
        PromiseBook.save(test.game, promise);

        test.isActive(test.sol, "action");
        PromiseDeals.observe(test.context());

        assertThat(PromiseBook.find(test.game, promise.id()).orElseThrow().status())
                .isEqualTo(Promise.Status.EXPIRED);
        assertThat(PromiseDeals.openFor(test.game, test.sol, promise.id())).isEmpty();
    }

    // Shard of the Throne gives the elected player a point, so Sol (an AI) will not sell its votes to elect Nekro for
    // a single trade good, but it takes a price that covers the point and the rival's gain.
    @Test
    void anAiSellsItsVotesOnlyForWhatTheyCostIt() {
        AiTestGame test = AiTestGame.withSolAi();
        test.place("01", "304");
        test.sol.addPlanet("jord");
        test.game.setVp(10);
        test.game.setStoredValue("agendaCount", "2");
        test.game.setCurrentAgendaInfo(SHARD);
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":4,\"black\":0}");
        test.isActive(test.sol, "agendawaiting");
        test.nekro.setTg(40);
        Promise cheap = Promise.offer(
                test.game,
                Promise.Term.VOTE_FOR,
                test.sol,
                test.nekro,
                test.nekro,
                PromisedVotes.agendaKey(test.game),
                "nekro",
                4,
                1,
                NOW,
                NOW + 60_000);
        PromiseBook.save(test.game, cheap);

        AiTurnContext context = test.contextFor(test.sol, Set.of(), NOW, offerPrompt(test.sol, cheap));
        assertThat(AiTestGame.pressedId(PromiseDeals.answer(context).orElseThrow()))
                .isEqualTo("FFCC_sol_" + PromiseDeals.DECLINE_PREFIX + cheap.id());

        Promise generous = new Promise(
                cheap.id(),
                cheap.term(),
                cheap.promiser(),
                cheap.promisee(),
                cheap.proposer(),
                cheap.round(),
                cheap.agenda(),
                cheap.target(),
                cheap.votes(),
                40,
                cheap.status(),
                cheap.offeredAt(),
                cheap.expiresAt(),
                List.of(),
                List.of());
        PromiseBook.save(test.game, generous);
        assertThat(AiTestGame.pressedId(PromiseDeals.answer(context).orElseThrow()))
                .isEqualTo("FFCC_sol_" + PromiseDeals.ACCEPT_PREFIX + cheap.id());
    }

    // Agenda phase: Xxcha (an AI, 4 points) holds the bigger bloc on Shard of the Throne. Sol (an AI) wants the point.
    // Xxcha's 2 votes are worth about 8.3 TG to Sol and cost Xxcha about 6.6, so on the last agenda Xxcha takes no
    // less than its floor of 8 (cost plus the 0.5 minimum). That leaves Sol under its own 0.5 minimum, so Sol makes no
    // offer it knows would be refused (it used to offer 7). Sol's offers in ConditionalVoteDealsTest cover the post.
    @Test
    void anAiOffersOnlyAPriceTheSellerTakes() {
        AiTestGame test = AiTestGame.withSolAi();
        Player xxcha = test.addSeat(THIRD_AI_ID, "xxcha", "red");
        test.game.setVp(10);
        test.place("01", "304");
        test.sol.addPlanet("jord");
        test.place("19", "310");
        xxcha.addPlanet("wellon");
        test.points(xxcha, 4);
        test.game.setStoredValue("agendaCount", "2");
        test.game.setCurrentAgendaInfo(SHARD);
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":2,\"red\":5,\"black\":0}");
        test.isActive(test.sol, "agendawaiting");
        test.sol.setTg(10);
        AiTurnContext context = test.contextFor(test.sol, Set.of(), NOW, table());

        VoteDeals.Terms terms = VoteDeals.valuedAt(test.game, test.sol, xxcha, test.sol, "sol", 2)
                .orElseThrow();
        int floor = PromiseDeals.voteFloor(terms.sellerCost(), terms.chance());
        assertThat(terms.buyerGain() - floor).isLessThan(0.5);

        assertThat(PromiseDeals.proposeVotes(context, List.of(xxcha))).isEmpty();
        assertThat(PromiseBook.proposedBy(test.game, test.sol)).isEmpty();
    }

    // A seat that has started its ballot (pressed vote and stored the outcome it backs) can no longer change the
    // outcome, only the number of votes, so it refuses even a generous offer for another outcome: accepting would
    // break the promise. An offer for the outcome it already backs is still open to it.
    @Test
    void aSeatInTheMiddleOfItsBallotSellsNoVotesForAnotherOutcome() {
        AiTestGame test = AiTestGame.withSolAi();
        test.place("01", "304");
        test.sol.addPlanet("jord");
        test.game.setVp(10);
        test.game.setStoredValue("agendaCount", "2");
        test.game.setCurrentAgendaInfo(SHARD);
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":4,\"black\":0}");
        test.isActive(test.sol, "agendawaiting");
        test.nekro.setTg(40);
        Promise generous = Promise.offer(
                test.game,
                Promise.Term.VOTE_FOR,
                test.sol,
                test.nekro,
                test.nekro,
                PromisedVotes.agendaKey(test.game),
                "nekro",
                4,
                40,
                NOW,
                NOW + 60_000);
        PromiseBook.save(test.game, generous);
        AiTurnContext context = test.contextFor(test.sol, Set.of(), NOW, offerPrompt(test.sol, generous));
        assertThat(PromiseDeals.accepts(context, generous)).isTrue();

        // The ballot AgendaVoting stores once the seat presses "vote": outcome, planet owner, votes, Predictive.
        context.memory().put("agendaBallot|" + test.game.getCurrentAgendaInfo(), "sol~~4~false");

        assertThat(PromiseDeals.accepts(context, generous)).isFalse();
        assertThat(PromiseDeals.backedOutcome(context).test("sol")).isTrue();
        assertThat(PromiseDeals.backedOutcome(context).test("nekro")).isFalse();
    }

    // Self-play trace: the speaker abstained (planning to pick the outcome by tie-break), then accepted a vote sale it
    // could no longer cast, so the promise was never delivered and the buyer never paid. Once a seat has abstained on
    // this agenda it sells no votes on it, even for the outcome it means to pick.
    @Test
    void aSeatThatAbstainedSellsNoVotesOnThatAgenda() {
        AiTestGame test = AiTestGame.withSolAi();
        test.place("01", "304");
        test.sol.addPlanet("jord");
        test.game.setVp(10);
        test.game.setStoredValue("agendaCount", "2");
        test.game.setCurrentAgendaInfo(SHARD);
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":4,\"black\":0}");
        test.isActive(test.sol, "agendawaiting");
        test.nekro.setTg(40);
        Promise forSol = Promise.offer(
                test.game,
                Promise.Term.VOTE_FOR,
                test.sol,
                test.nekro,
                test.nekro,
                PromisedVotes.agendaKey(test.game),
                "sol",
                4,
                40,
                NOW,
                NOW + 60_000);
        PromiseBook.save(test.game, forSol);
        AiTurnContext context = test.contextFor(test.sol, Set.of(), NOW, offerPrompt(test.sol, forSol));
        assertThat(PromiseDeals.accepts(context, forSol)).isTrue();

        AgendaVoting.markAbstained(context);

        assertThat(AgendaVoting.abstained(context)).isTrue();
        assertThat(PromiseDeals.accepts(context, forSol)).isFalse();
    }

    // A seat that promised its votes to one outcome offers to buy votes only for that outcome: buying for another
    // would mean it means to break its own promise.
    @Test
    void aSeatThatPromisedItsVotesBuysOnlyForThePromisedOutcome() {
        AiTestGame test = AiTestGame.withSolAi();
        Player xxcha = test.addSeat(THIRD_AI_ID, "xxcha", "red");
        test.game.setVp(10);
        test.place("01", "304");
        test.sol.addPlanet("jord");
        test.place("19", "310");
        xxcha.addPlanet("wellon");
        test.points(xxcha, 4);
        test.game.setStoredValue("agendaCount", "2");
        test.game.setCurrentAgendaInfo(SHARD);
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":2,\"red\":5,\"black\":0}");
        test.isActive(test.sol, "agendawaiting");
        test.sol.setTg(7);
        Promise sold = Promise.offer(
                test.game,
                Promise.Term.VOTE_FOR,
                test.sol,
                test.nekro,
                test.nekro,
                PromisedVotes.agendaKey(test.game),
                "nekro",
                2,
                1,
                NOW,
                NOW + 60_000);
        PromiseBook.save(test.game, sold.accepted(test.game));
        AiTurnContext context = test.contextFor(test.sol, Set.of(), NOW, table());

        PromiseDeals.proposeVotes(context, List.of(xxcha));

        assertThat(PromiseBook.proposedBy(test.game, test.sol)).allMatch(promise -> "nekro".equals(promise.target()));
        assertThat(PromiseDeals.backedOutcome(context).test("sol")).isFalse();
    }

    // Traces-4b, Executive Sanctions: Hacan offered Nekro its 1 vote for For at 4 TG, after Nekro predicted For, and
    // Nekro took it. A vote toward its Galactic Threat prediction is worth at most 2 TG to Nekro.
    private static Promise threatVoteOffer(AiTestGame test, int price) {
        test.game.setVp(10);
        test.game.setStoredValue("agendaCount", "1");
        test.game.setCurrentAgendaInfo("Law_For/Against_7_sanctions");
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":6,\"black\":0}");
        test.game.setCurrentAgendaVote("for", "black_" + GalacticThreatCost.RIDER);
        test.place("01", "304");
        test.sol.addPlanet("jord");
        test.place("19", "310");
        test.sol.addPlanet("wellon");
        test.sol.addTech("gd");
        test.isActive(test.nekro, "agendawaiting");
        test.nekro.setTg(10);
        Promise promise = Promise.offer(
                test.game,
                Promise.Term.VOTE_FOR,
                test.sol,
                test.nekro,
                test.sol,
                PromisedVotes.agendaKey(test.game),
                "for",
                1,
                price,
                NOW,
                NOW + 60_000);
        PromiseBook.save(test.game, promise);
        return promise;
    }

    @Test
    void nekroPaysNoMoreThanTwoTradeGoodsForAVoteTowardItsPrediction() {
        AiTestGame test = AiTestGame.withSolAi();
        Promise dear = threatVoteOffer(test, 4);
        assertThat(PromiseDeals.predictionCap(test.game, test.nekro, "for"))
                .isEqualTo(PromiseDeals.PREDICTION_VOTE_PRICE_CAP);
        assertThat(PromiseDeals.predictionCap(test.game, test.nekro, "against")).isZero();

        AiTurnContext context = test.contextFor(test.nekro, Set.of(), NOW, offerPrompt(test.nekro, dear));
        assertThat(PromiseDeals.accepts(context, dear)).isFalse();
    }

    @Test
    void nekroTakesATwoTradeGoodVoteTowardItsPrediction() {
        AiTestGame test = AiTestGame.withSolAi();
        Promise fair = threatVoteOffer(test, 2);

        AiTurnContext context = test.contextFor(test.nekro, Set.of(), NOW, offerPrompt(test.nekro, fair));
        assertThat(PromiseDeals.accepts(context, fair)).isTrue();
    }

    @Test
    void controlSeatHasNoPredictionPriceCap() {
        AiTestGame test = AiTestGame.withSolAi();
        Promise dear = threatVoteOffer(test, 4);
        AiExperiments.disable(test.nekro, Set.of(Experiment.AGENDA_VALUE_TABLE));

        assertThat(PromiseDeals.predictionCap(test.game, test.nekro, "for")).isZero();
        AiTurnContext context = test.contextFor(test.nekro, Set.of(), NOW, offerPrompt(test.nekro, dear));
        assertThat(PromiseDeals.accepts(context, dear)).isTrue();
    }

    // The 2 TG cap is a budget for the predicted outcome, not a per-deal limit: once Nekro owes 2 TG toward For on this
    // agenda (an accepted vote deal with another seat), it takes no further vote toward For at any price.
    @Test
    void thePredictionCapIsABudgetForTheWholeAgenda() {
        AiTestGame test = AiTestGame.withSolAi();
        Player xxcha = test.addSeat("200000000000000002", "xxcha", "red");
        Promise second = threatVoteOffer(test, 1);
        Promise first = Promise.offer(
                        test.game,
                        Promise.Term.VOTE_FOR,
                        xxcha,
                        test.nekro,
                        test.nekro,
                        PromisedVotes.agendaKey(test.game),
                        "for",
                        1,
                        2,
                        NOW,
                        NOW + 60_000)
                .accepted(test.game);
        PromiseBook.save(test.game, first);

        assertThat(PromiseDeals.promisedToward(test.game, test.nekro, "for", ""))
                .isEqualTo(2);
        AiTurnContext context = test.contextFor(test.nekro, Set.of(), NOW, offerPrompt(test.nekro, second));
        assertThat(PromiseDeals.accepts(context, second)).isFalse();
    }

    // Flag discipline: a seller's own asking cap follows its own experiment flag, not the buyer's.
    @Test
    void theSellersCapFollowsTheSellersFlag() {
        AiTestGame test = AiTestGame.withSolAi();
        threatVoteOffer(test, 2);
        AiExperiments.disable(test.sol, Set.of(Experiment.AGENDA_VALUE_TABLE));

        assertThat(PromiseDeals.predictionCap(test.game, test.sol, test.nekro, "for"))
                .isZero();
        assertThat(PromiseDeals.predictionCap(test.game, test.nekro, test.nekro, "for"))
                .isEqualTo(PromiseDeals.PREDICTION_VOTE_PRICE_CAP);
    }
}
