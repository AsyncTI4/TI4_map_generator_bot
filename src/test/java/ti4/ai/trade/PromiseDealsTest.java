package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static ti4.ai.AiTestGame.NOW;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.agenda.PromisedVotes;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
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

    // Nekro has already activated Sol's home this round, so it could not attack it anyway: the promise costs it
    // nothing, while Sol fears losing its home. Nekro sells Sol a promise for the 3 trade goods Sol has.
    @Test
    void sellsPeaceItWasNotGoingToBreakAnyway() {
        AiTestGame test = new AiTestGame();
        Tile jord = threatenSolHome(test);
        jord.addCC(Mapper.getCCID(test.nekro.getColor()));
        test.sol.setTg(3);
        test.aiIsActive("action");

        Optional<AiDecision> decision = PromiseDeals.proposeOwnTurn(test.context(table()), List.of(test.sol));

        AiDecision.Post post = (AiDecision.Post) decision.orElseThrow();
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
        threatenSolHome(test).addCC(Mapper.getCCID(test.nekro.getColor()));
        test.sol.setTg(0);
        test.aiIsActive("action");

        assertThat(PromiseDeals.proposeOwnTurn(test.context(table()), List.of(test.sol)))
                .isEmpty();
    }

    @Test
    void controlSeatOffersNoPromises() {
        AiTestGame test = new AiTestGame();
        threatenSolHome(test).addCC(Mapper.getCCID(test.nekro.getColor()));
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
        test.game.setVp(10);
        test.aiIsActive("action");
        Promise promise = offer(test, Promise.Term.NO_ATTACK, test.sol, test.nekro, test.nekro, 2);
        PromiseBook.save(test.game, promise);

        AiTurnContext context = test.contextFor(test.sol, Set.of(), NOW, offerPrompt(test.sol, promise));

        assertThat(AiTestGame.pressedId(PromiseDeals.answer(context).orElseThrow()))
                .isEqualTo("FFCC_sol_" + PromiseDeals.ACCEPT_PREFIX + promise.id());
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
}
