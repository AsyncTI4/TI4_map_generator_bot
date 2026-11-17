package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.agenda.PromisedVotes;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.ai.promise.PromisePayment;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The user's ask (2026-11-16): when a deal is declined, the proposer comes back once with a better offer, if it still
// clears its own minimum gain. A seller asks 1 TG less, a buyer offers 1 more; one decline is allowed per pair and
// scope (the agenda for vote deals, the round otherwise).
class ReofferTest extends BaseTi4Test {

    private static final String EXECUTION = "Directive_Elect Player_7_execution";

    private AiTestGame test;
    private Player sol;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        xxcha = test.addSeat("7100000555555555", "xxcha", "red");
        test.game.setStoredValue("agendaCount", "1");
        test.game.setCurrentAgendaInfo(EXECUTION);
        sol.setTg(5);
    }

    private Promise declinedSale(int price) {
        Promise offer = Promise.offer(
                test.game,
                Promise.Term.VOTE_FOR,
                sol,
                xxcha,
                sol,
                PromisedVotes.agendaKey(test.game),
                "hacan",
                3,
                price,
                NOW,
                NOW + 60_000);
        Promise declined = offer.with(Promise.Status.DECLINED);
        PromiseBook.save(test.game, declined);
        return declined;
    }

    private PromiseDeals.Proposal sale(int price, double gain) {
        return new PromiseDeals.Proposal(Promise.Term.VOTE_FOR, sol, xxcha, "hacan", 3, price, gain);
    }

    @Test
    void aFreshDealIsPostedAsIs() {
        PromiseDeals.Proposal proposal = sale(3, 2.0);
        assertThat(Reoffer.improved(test.game, sol, proposal)).contains(proposal);
    }

    // Xxcha turned down Sol's votes at 3 TG: Sol asks 2 TG next time.
    @Test
    void aSellerAsksLessAfterADecline() {
        declinedSale(3);

        PromiseDeals.Proposal again =
                Reoffer.improved(test.game, sol, sale(3, 2.0)).orElseThrow();
        assertThat(again.price()).isEqualTo(2);
        assertThat(again.payment()).isEqualTo(PromisePayment.tradeGoods(2));
    }

    // At 1 TG there is nothing lower to ask, so Sol does not repeat the offer.
    @Test
    void aSellerAtTheFloorDoesNotRepeatItself() {
        declinedSale(1);
        assertThat(Reoffer.improved(test.game, sol, sale(1, 2.0))).isEmpty();
    }

    // Cutting the price would leave Sol under its own minimum gain, so it lets the deal go.
    @Test
    void noReofferBelowTheProposersMinimum() {
        declinedSale(3);
        assertThat(Reoffer.improved(test.game, sol, sale(3, TradeValue.PROPOSE_MIN)))
                .isEmpty();
    }

    // One decline still leaves room for a deal; a second one ends it.
    @Test
    void oneDeclineIsAllowedPerPair() {
        Promise first = declinedSale(3);
        assertThat(PromiseDeals.noVoteDealYet(test.game, sol, xxcha)).isTrue();
        assertThat(Reoffer.mayDeal(List.of(first, first.with(Promise.Status.DECLINED))))
                .isFalse();
        assertThat(Reoffer.mayDeal(List.of(first.with(Promise.Status.OFFERED)))).isFalse();
    }

    // A declined deal that pays now frees the pair's trade window again.
    @Test
    void aDeclinedPayNowDealFreesTheWindow() {
        test.game.setPhaseOfGame("action");
        test.isActive(sol, "action");
        AiTurnContext context = test.contextFor(sol, Set.of(), NOW, new ti4.ai.perception.AiPrompt[0]);
        TradeLegality.useWindow(context, xxcha);
        Promise offer =
                Promise.offer(test.game, Promise.Term.NO_ATTACK, sol, xxcha, sol, "", "", 0, 2, NOW, NOW + 60_000);
        PromiseBook.save(test.game, offer.with(Promise.Status.DECLINED));
        context.memory().put("tradeWindow|" + TradeLegality.window(test.game) + "|xxcha", String.valueOf(NOW));

        Reoffer.freeDeclinedWindows(context);
        assertThat(TradeLegality.windowUsed(context, xxcha)).isFalse();
    }
}
