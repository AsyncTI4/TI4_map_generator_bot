package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

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
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Deals cannot promise a strategy card, so a small payment to the Technology holder is read from who sends it. A seat
// that can follow now pays to have Technology played early (for Gravity Drive); a seat that cannot follow yet, but
// could after another turn, pays to have it held. Nekro (the AI) holds Technology in round 1; Sol, its neighbour, is a
// second AI. Hacan, Letnev and Arborec sit at the table without trade goods or commodities, so they cannot be denied
// the secondary; with four opponents, denying Sol alone is worth less to Nekro than a trade good.
class TechnologyRequestTest extends BaseTi4Test {

    private static final int TECHNOLOGY = 7;

    private TradeTable table;
    private Player nekro;
    private Player sol;

    @BeforeEach
    void setUp() {
        table = TradeTable.withAiSol();
        table.game.setRound(1);
        table.nekroAndSolNeighbour();
        table.stock(table.aiSeat("hacan", "red"), 0, 0);
        table.stock(table.secondAiSeat("letnev", "green"), 0, 0);
        table.stock(table.humanSeat("arborec", "yellow"), 0, 0);
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

    private AiTurnContext nekroContext() {
        return table.context();
    }

    private AiTurnContext solContext() {
        return table.contextFor(sol);
    }

    @Test
    void readsAGiftFromASeatThatCanFollowAsARequestToPlayEarly() {
        solCanFollow();

        TechnologyRequest.noteAccepted(nekroContext(), sol, deal("sendingsol_receivingnekro_TGs_1"));

        assertThat(table.test.memory.get(OpeningNeeds.technologyRequestedKey(table.game)))
                .contains("sol");
        assertThat(table.test.memory.has(TechnologyTiming.heldKey(table.game))).isFalse();
    }

    @Test
    void readsAPaymentFromASeatThatCannotFollowYetAsARequestToWait() {
        solCanFollowLater();

        TechnologyRequest.noteAccepted(nekroContext(), sol, deal("sendingsol_receivingnekro_SendDebt_1"));

        assertThat(table.test.memory.get(TechnologyTiming.heldKey(table.game))).contains("sol");
        assertThat(table.test.memory.has(OpeningNeeds.technologyRequestedKey(table.game)))
                .isFalse();
    }

    // A trade that asks for something back is a trade, not a request.
    @Test
    void doesNotReadATradeAsARequest() {
        solCanFollow();
        Deal trade = deal("sendingsol_receivingnekro_TGs_1", "sendingnekro_receivingsol_Comms_2");

        TechnologyRequest.noteAccepted(nekroContext(), sol, trade);

        assertThat(table.test.memory.has(OpeningNeeds.technologyRequestedKey(table.game)))
                .isFalse();
        assertThat(TechnologyRequest.accepts(nekroContext(), sol, trade)).isEmpty();
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

        assertThat(TechnologyRequest.accepts(nekroContext(), sol, deal("sendingsol_receivingnekro_SendDebt_1")))
                .contains(true);
    }

    @Test
    void holderWillNotWaitAfterBeingPaidToPlayEarly() {
        solCanFollowLater();
        table.test.memory.put(OpeningNeeds.technologyRequestedKey(table.game), "hacan");

        assertThat(TechnologyRequest.accepts(nekroContext(), sol, deal("sendingsol_receivingnekro_SendDebt_1")))
                .contains(false);
    }

    @Test
    void holderWillNotPlayEarlyAfterBeingPaidToWait() {
        solCanFollow();
        table.test.memory.put(TechnologyTiming.heldKey(table.game), "hacan");

        assertThat(TechnologyRequest.accepts(nekroContext(), sol, deal("sendingsol_receivingnekro_TGs_1")))
                .contains(false);
    }

    // The holder takes the payment and records the hold, so it will not play Technology before Sol can follow.
    @Test
    void acceptingAPaymentToWaitHoldsTechnologyBack() {
        solCanFollowLater();
        AiPrompt offer = offerFromSol("sendingsol_receivingnekro_SendDebt_1");

        Optional<AiDecision> decision = OfferResponder.answer(table.test.contextFor(nekro, Set.of(), NOW, offer));

        assertThat(decision.map(AiTestGame::pressedId)).contains("acceptOffer_blue_1");
        assertThat(TechnologyTiming.heldBack(table.game, nekro, table.test.memory))
                .isTrue();
    }

    @Test
    void followerPaysTheHolderToWaitWhenItCanFollowLater() {
        solCanFollowLater();

        assertThat(TechnologyRequest.accepts(solContext(), nekro, deal("sendingsol_receivingnekro_SendDebt_1")))
                .contains(true);
    }

    @Test
    void followerDoesNotPayForAWaitItDoesNotNeed() {
        solCanFollow();

        assertThat(TechnologyRequest.accepts(solContext(), nekro, deal("sendingsol_receivingnekro_TGs_1")))
                .contains(false);
    }

    // Paying a trade good would leave Sol unable to follow even after washing, so it offers a debt instead.
    @Test
    void followerOffersDebtWhenATradeGoodWouldCostItTheFollow() {
        table.stock(sol, 3, 1);
        table.changed();

        assertThat(TechnologyRequest.payment(table.game, sol, nekro))
                .contains(deal("sendingsol_receivingnekro_SendDebt_1"));

        table.stock(sol, 4, 1);
        table.changed();

        assertThat(TechnologyRequest.payment(table.game, sol, nekro)).contains(deal("sendingsol_receivingnekro_TGs_1"));
    }

    @Test
    void followerOffersToPayTheHolderToWait() {
        solCanFollowLater();
        table.test.isActive(sol, "action");

        Optional<AiDecision> started = TechnologyRequest.offerToWait(solContextWithEntry(), List.of(nekro));

        assertThat(started).isPresent();
        Draft draft = Draft.read(table.test.memoryOf(sol)).orElseThrow();
        assertThat(draft.purpose()).isEqualTo(Purpose.TECHNOLOGY_HOLD);
        assertThat(draft.target()).isEqualTo(deal("sendingsol_receivingnekro_SendDebt_1"));
    }

    @Test
    void holderAsksAFollowerToPayForTheWait() {
        solCanFollowLater();

        Optional<AiDecision> started = TechnologyRequest.askToWait(nekroContextWithEntry(), List.of(sol));

        assertThat(started).isPresent();
        Draft draft = Draft.read(table.test.memory).orElseThrow();
        assertThat(draft.purpose()).isEqualTo(Purpose.TECHNOLOGY_HOLD);
        assertThat(draft.target()).isEqualTo(deal("sendingsol_receivingnekro_SendDebt_1"));
    }

    @Test
    void holderDoesNotAskSeatsThatCanAlreadyFollow() {
        solCanFollow();

        assertThat(TechnologyRequest.askToWait(nekroContextWithEntry(), List.of(sol)))
                .isEmpty();
    }

    @Test
    void holderHoldsOnceItsAskIsAccepted() {
        solCanFollowLater();

        TechnologyRequest.noteProposalAccepted(nekroContext(), sol, Purpose.TECHNOLOGY_HOLD);

        assertThat(table.test.memory.get(TechnologyTiming.heldKey(table.game))).contains("sol");
    }

    @Test
    void noHoldDealsAfterRoundTwo() {
        solCanFollowLater();
        table.game.setRound(3);

        assertThat(TechnologyRequest.accepts(nekroContext(), sol, deal("sendingsol_receivingnekro_SendDebt_1")))
                .isEmpty();
    }

    private AiPrompt offerFromSol(String... items) {
        sol.getTransactionItems().clear();
        for (String item : items) sol.addTransactionItem(item);
        table.game.setStoredValue("offerFromsolTonekro", "1");
        return TradeButtons.incoming("offer", NOW - 1_000L, sol, 1);
    }

    private AiTurnContext nekroContextWithEntry() {
        return table.test.contextFor(nekro, Set.of(), NOW, TradeButtons.entry("various-nekro", NOW - 60_000L));
    }

    private AiTurnContext solContextWithEntry() {
        return table.test.contextFor(sol, Set.of(), NOW, TradeButtons.entry("various-sol", NOW - 60_000L));
    }
}
