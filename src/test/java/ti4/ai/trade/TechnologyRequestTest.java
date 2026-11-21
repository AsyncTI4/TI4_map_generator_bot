package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.OpeningNeeds;
import ti4.ai.strategy.StrategyCardTurn;
import ti4.ai.strategy.TechnologyTiming;
import ti4.ai.trade.TechnologyRequest.Ask;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Deals cannot promise a strategy card, so an AI that pays the Technology holder (or asks the holder's price) first
// says so at the table, and the holder reads the ask from that message. A seat that can follow now pays to have
// Technology played early (for Gravity Drive); a seat that cannot follow yet, but could after another turn, pays to
// have it held. Only a human payer, who posts no such message, has the ask inferred from what it can afford.
// Nekro (the AI) holds Technology in round 1; Sol, its neighbour, is a second AI. Hacan, Letnev, Arborec and Yssaril
// sit at the table without trade goods or commodities, so they cannot be denied the secondary; with five opponents,
// denying Sol alone is worth less to Nekro than a trade good (Sol's own research rates make Gravity Drive worth more
// to it than to a faction without data, so a smaller table would put its denial above a single debt).
class TechnologyRequestTest extends BaseTi4Test {

    private static final int TECHNOLOGY = 7;
    private static final String SIXTH_SEAT_ID = "200000000000000077";
    private static final String HOLD_DEBT = "sendingsol_receivingnekro_SendDebt_1";
    private static final String EARLY_TRADE_GOOD = "sendingsol_receivingnekro_TGs_1";

    private TradeTable table;
    private Player nekro;
    private Player sol;
    private Player arborec;
    private final List<AiPrompt> tableTalk = new ArrayList<>();

    @BeforeEach
    void setUp() {
        table = TradeTable.withAiSol();
        table.game.setRound(1);
        table.nekroAndSolNeighbour();
        table.stock(table.aiSeat("hacan", "red"), 0, 0);
        table.stock(table.secondAiSeat("letnev", "green"), 0, 0);
        arborec = table.humanSeat("arborec", "yellow");
        table.stock(arborec, 0, 0);
        table.stock(table.test.addSeat(SIXTH_SEAT_ID, "yssaril", "purple"), 0, 0);
        nekro = table.nekro;
        sol = table.sol;
        nekro.addSC(TECHNOLOGY);
        table.changed();
    }

    private void solCanFollow() {
        table.stock(sol, 0, 5);
        table.changed();
    }

    // Sol has a strategy token but no trade goods; its four commodities become trade goods once washed.
    private void solCanFollowLater() {
        table.stock(sol, 4, 0);
        table.changed();
    }

    private static Deal deal(String... raws) {
        return new Deal(Arrays.stream(raws)
                .map(raw -> DealItem.parse(raw, "nekro", "sol").orElseThrow())
                .toList());
    }

    private void said(String text, long minutesAgo) {
        AiPrompt message =
                prompt("talk-" + tableTalk.size(), PromptSource.PUBLIC, NOW - minutesAgo * 60_000L, new String[0]);
        tableTalk.add(AiTestGame.withContent(message, text));
    }

    private void solAnnounces(String item, Ask ask) {
        said(TechnologyAnnouncements.payment(sol, nekro, deal(item), ask), 1);
    }

    private AiTurnContext contextFor(Player seat, AiPrompt... more) {
        List<AiPrompt> prompts = new ArrayList<>(tableTalk);
        prompts.addAll(List.of(more));
        prompts.add(TradeButtons.entry("various-" + seat.getFaction(), NOW - 60_000L));
        return table.test.contextFor(seat, Set.of(), NOW, prompts.toArray(AiPrompt[]::new));
    }

    @Test
    void announcementsAreTableTalkTheAiCanRead() {
        String text = TechnologyAnnouncements.payment(sol, nekro, deal(HOLD_DEBT), Ask.HOLD);

        assertThat(text).startsWith(AiPrompt.TABLE_TALK).endsWith("1 debt to ask them to hold Technology until later.");
        assertThat(TechnologyAnnouncements.fee(nekro, sol, deal(EARLY_TRADE_GOOD)))
                .endsWith("for 1 trade good to hold Technology until they can follow.");
    }

    @Test
    void readsAnAnnouncedRequestToPlayEarly() {
        solCanFollow();
        solAnnounces(EARLY_TRADE_GOOD, Ask.EARLY);

        TechnologyRequest.noteAccepted(contextFor(nekro), sol, deal(EARLY_TRADE_GOOD));

        assertThat(table.test.memory.get(OpeningNeeds.technologyRequestedKey(table.game)))
                .contains("sol");
        assertThat(table.test.memory.has(TechnologyTiming.heldKey(table.game))).isFalse();
    }

    // The message, not Sol's purse, says what the payment is for: Sol could follow now, but it asked for a hold.
    @Test
    void readsAnAnnouncedRequestToWaitWhateverThePayerCanAfford() {
        solCanFollow();
        solAnnounces(HOLD_DEBT, Ask.HOLD);

        TechnologyRequest.noteAccepted(contextFor(nekro), sol, deal(HOLD_DEBT));

        assertThat(table.test.memory.get(TechnologyTiming.heldKey(table.game)))
                .hasValueSatisfying(value -> assertThat(value).startsWith("sol"));
        assertThat(table.test.memory.has(OpeningNeeds.technologyRequestedKey(table.game)))
                .isFalse();
    }

    // An AI always announces its Technology asks, so an unannounced gift from one is just a gift.
    @Test
    void ignoresAnUnannouncedGiftFromAnAi() {
        solCanFollowLater();

        TechnologyRequest.noteAccepted(contextFor(nekro), sol, deal(HOLD_DEBT));

        assertThat(TechnologyRequest.accepts(contextFor(nekro), sol, deal(HOLD_DEBT)))
                .isEmpty();
        assertThat(table.test.memory.has(TechnologyTiming.heldKey(table.game))).isFalse();
    }

    // An announcement older than half an hour belongs to some earlier exchange.
    @Test
    void ignoresAStaleAnnouncement() {
        solCanFollowLater();
        said(TechnologyAnnouncements.payment(sol, nekro, deal(HOLD_DEBT), Ask.HOLD), 45);

        assertThat(TechnologyRequest.askOf(contextFor(nekro), sol)).isEmpty();
    }

    // A human posts no announcement, so the ask is read from what it can afford: Arborec cannot follow yet.
    @Test
    void infersAHumanPayersAskFromWhatItCanAfford() {
        table.stock(arborec, 4, 0);
        table.changed();

        assertThat(TechnologyRequest.askOf(contextFor(nekro), arborec)).contains(Ask.HOLD);

        table.stock(arborec, 0, 5);
        table.changed();

        assertThat(TechnologyRequest.askOf(contextFor(nekro), arborec)).contains(Ask.EARLY);
    }

    // A trade that asks for something back is a trade, not a request.
    @Test
    void doesNotReadATradeAsARequest() {
        solCanFollow();
        solAnnounces(EARLY_TRADE_GOOD, Ask.EARLY);
        Deal trade = deal(EARLY_TRADE_GOOD, "sendingnekro_receivingsol_Comms_2");

        TechnologyRequest.noteAccepted(contextFor(nekro), sol, trade);

        assertThat(table.test.memory.has(OpeningNeeds.technologyRequestedKey(table.game)))
                .isFalse();
        assertThat(TechnologyRequest.accepts(contextFor(nekro), sol, trade)).isEmpty();
    }

    @Test
    void playsTechnologyOnceAskedTo() {
        AiTestGame test = AiTestGame.withSolAi();
        test.game.setRound(1);
        test.nekro.addSC(TECHNOLOGY);
        test.aiIsActive("action");
        test.memory.put(OpeningNeeds.technologyRequestedKey(test.game), "sol");
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_strategicAction_7");
        AiTurnContext context = test.contextFor(test.nekro, Set.of(), NOW, turn);

        assertThat(StrategyCardTurn.playIfDue(context, List.of(turn)).map(AiTestGame::pressedId))
                .contains("FFCC_nekro_strategicAction_7");
    }

    // Nekro has no planet left to take, so it would play Technology now and deny Sol; one debt from Sol outweighs
    // what that denial is worth to Nekro at a five-seat table.
    @Test
    void holderAcceptsAPaymentToWait() {
        solCanFollowLater();
        solAnnounces(HOLD_DEBT, Ask.HOLD);

        assertThat(TechnologyRequest.accepts(contextFor(nekro), sol, deal(HOLD_DEBT)))
                .contains(true);
    }

    @Test
    void holderWillNotWaitAfterBeingPaidToPlayEarly() {
        solCanFollowLater();
        solAnnounces(HOLD_DEBT, Ask.HOLD);
        table.test.memory.put(OpeningNeeds.technologyRequestedKey(table.game), "hacan");

        assertThat(TechnologyRequest.accepts(contextFor(nekro), sol, deal(HOLD_DEBT)))
                .contains(false);
    }

    @Test
    void holderWillNotPlayEarlyAfterBeingPaidToWait() {
        solCanFollow();
        solAnnounces(EARLY_TRADE_GOOD, Ask.EARLY);
        TechnologyTiming.grantHold(table.game, table.test.memory, arborec);

        assertThat(TechnologyRequest.accepts(contextFor(nekro), sol, deal(EARLY_TRADE_GOOD)))
                .contains(false);
    }

    // The holder takes the payment and records the hold, so it will not play Technology before Sol can follow.
    @Test
    void acceptingAPaymentToWaitHoldsTechnologyBack() {
        solCanFollowLater();
        solAnnounces(HOLD_DEBT, Ask.HOLD);
        AiPrompt offer = offerFromSol(HOLD_DEBT);

        Optional<AiDecision> decision = OfferResponder.answer(contextFor(nekro, offer));

        assertThat(decision.map(AiTestGame::pressedId)).contains("acceptOffer_blue_1");
        assertThat(TechnologyTiming.heldBack(table.game, nekro, table.test.memory))
                .isTrue();
    }

    @Test
    void followerPaysTheHolderWhoAskedForItsPrice() {
        solCanFollowLater();
        said(TechnologyAnnouncements.fee(nekro, sol, deal(HOLD_DEBT)), 1);

        assertThat(TechnologyRequest.accepts(contextFor(sol), nekro, deal(HOLD_DEBT)))
                .contains(true);
    }

    @Test
    void followerTreatsAnUnannouncedDemandAsAnOrdinaryOffer() {
        solCanFollowLater();

        assertThat(TechnologyRequest.accepts(contextFor(sol), nekro, deal(HOLD_DEBT)))
                .isEmpty();
    }

    @Test
    void followerDoesNotPayForAWaitItDoesNotNeed() {
        solCanFollow();
        said(TechnologyAnnouncements.fee(nekro, sol, deal(EARLY_TRADE_GOOD)), 1);

        assertThat(TechnologyRequest.accepts(contextFor(sol), nekro, deal(EARLY_TRADE_GOOD)))
                .contains(false);
    }

    // Paying a trade good would leave Sol unable to follow even after washing, so it offers a debt instead.
    @Test
    void followerOffersDebtWhenATradeGoodWouldCostItTheFollow() {
        table.stock(sol, 3, 1);
        table.changed();

        assertThat(TechnologyRequest.payment(table.game, sol, nekro)).contains(deal(HOLD_DEBT));

        table.stock(sol, 4, 1);
        table.changed();

        assertThat(TechnologyRequest.payment(table.game, sol, nekro)).contains(deal(EARLY_TRADE_GOOD));
    }

    // The follower announces first and builds the offer on its next tick, so the holder sees why it is being paid.
    @Test
    void followerAnnouncesThenOffersToPayTheHolderToWait() {
        solCanFollowLater();
        table.test.isActive(sol, "action");

        Optional<AiDecision> announced = TechnologyRequest.announce(contextFor(sol), List.of(nekro));

        assertThat(announced)
                .containsInstanceOf(AiDecision.Announce.class)
                .hasValueSatisfying(decision -> assertThat(((AiDecision.Announce) decision).text())
                        .isEqualTo(TechnologyAnnouncements.payment(sol, nekro, deal(HOLD_DEBT), Ask.HOLD)));
        assertThat(TechnologyRequest.announce(contextFor(sol), List.of(nekro))).isEmpty();

        assertThat(TechnologyRequest.start(contextFor(sol), List.of(nekro))).isPresent();
        Draft draft = Draft.read(table.test.memoryOf(sol)).orElseThrow();
        assertThat(draft.purpose()).isEqualTo(Purpose.TECHNOLOGY_HOLD);
        assertThat(draft.target()).isEqualTo(deal(HOLD_DEBT));
    }

    @Test
    void holderAnnouncesThenAsksAFollowerToPayForTheWait() {
        solCanFollowLater();

        Optional<AiDecision> announced = TechnologyRequest.announce(contextFor(nekro), List.of(sol));

        assertThat(announced)
                .hasValueSatisfying(decision -> assertThat(((AiDecision.Announce) decision).text())
                        .isEqualTo(TechnologyAnnouncements.fee(nekro, sol, deal(HOLD_DEBT))));
        assertThat(TechnologyRequest.start(contextFor(nekro), List.of(sol))).isPresent();
        Draft draft = Draft.read(table.test.memory).orElseThrow();
        assertThat(draft.purpose()).isEqualTo(Purpose.TECHNOLOGY_HOLD);
        assertThat(draft.target()).isEqualTo(deal(HOLD_DEBT));
    }

    // If the situation changed between the announcement and the build, the ask is dropped for the round.
    @Test
    void dropsAnAnnouncedAskThatNoLongerApplies() {
        solCanFollowLater();
        TechnologyRequest.announce(contextFor(nekro), List.of(sol));
        solCanFollow();

        assertThat(TechnologyRequest.start(contextFor(nekro), List.of(sol))).isEmpty();
        assertThat(TechnologyRequest.announce(contextFor(nekro), List.of(sol))).isEmpty();
    }

    @Test
    void holderDoesNotAskSeatsThatCanAlreadyFollow() {
        solCanFollow();

        assertThat(TechnologyRequest.plan(contextFor(nekro), List.of(sol))).isEmpty();
    }

    // The memoised plan must be the plan that evaluating every research timing from scratch gives, for either seat.
    @Test
    void memoisedPlanMatchesAFreshSearchInEveryState() {
        List<Optional<TechnologyRequest.Plan>> plans = new ArrayList<>();
        Runnable[] states = {
            () -> {},
            this::solCanFollow,
            this::solCanFollowLater,
            () -> {
                table.stock(arborec, 4, 0);
                table.changed();
            },
            () -> {
                table.stock(sol, 3, 1);
                table.changed();
            }
        };
        for (Runnable state : states) {
            state.run();
            for (Player seat : List.of(nekro, sol)) {
                Player partner = seat == nekro ? sol : nekro;
                List<Player> partners = List.of(partner, arborec);
                Optional<TechnologyRequest.Plan> fresh = TechnologyRequest.search(contextFor(seat), partners);
                Optional<TechnologyRequest.Plan> memoised = TechnologyRequest.plan(contextFor(seat), partners);
                assertThat(memoised).isEqualTo(fresh);
                plans.add(memoised);
            }
        }
        assertThat(plans).anyMatch(Optional::isPresent).anyMatch(Optional::isEmpty);
    }

    @Test
    void holderHoldsOnceItsAskIsAccepted() {
        solCanFollowLater();

        TechnologyRequest.noteProposalAccepted(contextFor(nekro), sol, Purpose.TECHNOLOGY_HOLD);

        assertThat(TechnologyTiming.heldBack(table.game, nekro, table.test.memory))
                .isTrue();
    }

    @Test
    void noHoldDealsAfterRoundTwo() {
        solCanFollowLater();
        solAnnounces(HOLD_DEBT, Ask.HOLD);
        table.game.setRound(3);

        assertThat(TechnologyRequest.accepts(contextFor(nekro), sol, deal(HOLD_DEBT)))
                .isEmpty();
    }

    private AiPrompt offerFromSol(String... items) {
        sol.getTransactionItems().clear();
        for (String item : items) sol.addTransactionItem(item);
        table.game.setStoredValue("offerFromsolTonekro", "1");
        return TradeButtons.incoming("offer", NOW - 1_000L, sol, 1);
    }
}
