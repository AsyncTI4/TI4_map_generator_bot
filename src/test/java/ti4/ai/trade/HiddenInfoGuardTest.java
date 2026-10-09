package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Valuing a deal for or against another seat must use only what the table can see: its points, trade goods,
 * commodities, debt, fragment counts, technologies, leaders, neighbours and play area. Sol's seat is replaced by a
 * spy that records any look at its secret objectives, action cards or promissory note hand.
 */
class HiddenInfoGuardTest extends BaseTi4Test {

    private static final double TRUST = 0.8;

    private TradeTable table;
    private Player spy;

    // Sol is an AI peer beside Nekro, both can score Negotiate Trade Routes, Hacan is the outlet everyone can wash
    // with, and a Mentak neighbour makes the Pillage check run. Sol holds secrets, action cards and notes.
    @BeforeEach
    void setUp() {
        table = TradeTable.withAiSol();
        table.test.nekroHome();
        table.solHome();
        table.presence(table.sol, TradeTable.BESIDE_NEKRO);
        table.presence(table.humanSeat("mentak", "yellow"), TradeTable.BESIDE_NEKRO);
        table.stock(table.aiSeat("hacan", "red"), 6, 0);
        table.reveal("trade_routes");
        table.stock(table.nekro, 3, 3);
        table.stock(table.sol, 4, 3);
        table.sol.setSecret("dhw");
        table.sol.setActionCard("sabo1");
        table.sol.setPromissoryNote("blue_ta", 17);
        table.nekro.addDebtTokens("blue", 2);
        spy = Mockito.spy(table.sol);
        table.game.getPlayers().put(spy.getUserID(), spy);
    }

    private Deal deal(String... raws) {
        return new Deal(Arrays.stream(raws)
                .map(raw -> DealItem.parse(raw, "nekro", "sol").orElseThrow())
                .toList());
    }

    private void neverPeeked() {
        verify(spy, never()).getSecretsUnscored();
        verify(spy, never()).getActionCards();
        verify(spy, never()).getPromissoryNotes();
    }

    @Test
    void valuingDealsWithoutNotesNeverPeeks() {
        List<Deal> deals = List.of(
                deal("sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_Comms_3"),
                deal(
                        "sendingnekro_receivingsol_Comms_3",
                        "sendingnekro_receivingsol_SendDebt_2",
                        "sendingsol_receivingnekro_TGs_2"),
                deal("sendingnekro_receivingsol_ClearDebt_2", "sendingsol_receivingnekro_TGs_2"),
                deal("sendingsol_receivingnekro_Frags_CRF1", "sendingnekro_receivingsol_TGs_1"),
                deal("sendingnekro_receivingsol_Frags_IRF1", "sendingsol_receivingnekro_TGs_1"),
                deal("sendingsol_receivingnekro_ACs_4", "sendingnekro_receivingsol_TGs_1"));

        for (Deal deal : deals) {
            TradeValue.of(table.game, table.nekro, spy, deal, TRUST);
            TradeValue.predictedFor(table.game, spy, table.nekro, deal);
            TradeValue.acceptable(table.game, table.nekro, spy, deal, TRUST);
            TradeValue.proposable(table.game, table.nekro, spy, deal, TRUST);
            TradeValue.commitmentOk(table.game, table.nekro, spy, deal);
            Stinginess.partnerGain(table.game, spy, table.nekro, deal);
            Stinginess.veto(table.game, table.nekro, spy, deal);
            Stinginess.pillageLeak(table.game, table.nekro, spy, deal);
            Stinginess.pillageLeak(table.game, spy, table.nekro, deal);
            for (DealItem item : deal.items()) {
                if (item.isTo("nekro")) ComponentValues.receive(table.game, table.nekro, spy, item, TRUST);
                if (item.isFrom("nekro")) ComponentValues.give(table.game, table.nekro, spy, item, TRUST, true);
            }
        }
        Stinginess.nearWin(table.game, spy);
        Stinginess.rivalry(table.game, table.nekro, spy);
        Stinginess.rivalry(table.game, spy, table.nekro);
        ComponentValues.ownCommodity(table.game, table.nekro, spy);
        ComponentValues.ownCommodity(table.game, spy, table.nekro);
        ComponentValues.debtReceivable(table.game, table.nekro, spy, 2, TRUST);
        TradeTerms.feeFor(table.game, table.nekro, spy, TRUST);
        TradeTerms.exclusionReason(table.game, table.nekro, spy, TRUST);
        Desperation.vpWorth(table.game, spy);
        Desperation.tradeGoodsShort(table.game, spy);

        neverPeeked();
    }

    // Answering Sol's offers, including working out counter-offers and an owed debt collection, also sticks to what
    // the table can see of Sol.
    @Test
    void answeringOffersNeverPeeks() {
        spy.addDebtTokens("black", 2);
        List<String[]> offers = List.of(
                new String[] {"sendingsol_receivingnekro_Comms_3", "sendingnekro_receivingsol_Comms_3"},
                new String[] {"sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_TGs_1"},
                new String[] {
                    "sendingnekro_receivingsol_Comms_3",
                    "sendingsol_receivingnekro_TGs_2",
                    "sendingsol_receivingnekro_ACs_4"
                },
                new String[] {"sendingsol_receivingnekro_ClearDebt_2", "sendingnekro_receivingsol_TGs_2"});
        int number = 0;
        for (String[] items : offers) {
            spy.getTransactionItems().clear();
            for (String item : items) spy.addTransactionItem(item);
            table.game.setStoredValue("offerFromsolTonekro", String.valueOf(++number));
            AiPrompt offer = TradeButtons.incoming("offer-" + number, AiTestGame.NOW, spy, number);
            OfferBuilder.abandon(table.context());
            OfferResponder.answer(table.test.contextFor(table.nekro, Set.of(), AiTestGame.NOW, offer));
        }

        neverPeeked();
    }

    // A note Sol offers is named in the offer (the AI reads it by the hand id in the deal); that one lookup is the
    // only reason to open Sol's hand.
    @Test
    void anOfferedNoteIsOnlyLookedUpByItsId() {
        List<String> callers = new ArrayList<>();
        doAnswer(invocation -> {
                    callers.add(caller());
                    return invocation.callRealMethod();
                })
                .when(spy)
                .getPromissoryNotes();
        Deal noteForTradeGoods = deal("sendingsol_receivingnekro_PNs_17", "sendingnekro_receivingsol_TGs_2");

        double utility = TradeValue.of(table.game, table.nekro, spy, noteForTradeGoods, TRUST)
                .utility();
        TradeValue.predictedFor(table.game, spy, table.nekro, noteForTradeGoods);
        Stinginess.partnerGain(table.game, spy, table.nekro, noteForTradeGoods);

        assertThat(utility).isFinite();
        assertThat(callers).isNotEmpty().allMatch("NotesForTrade.aliasOf"::equals);
        verify(spy, never()).getSecretsUnscored();
        verify(spy, never()).getActionCards();
    }

    // The production method that asked for the hand: the first AI frame below this test's own answer.
    private static String caller() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .filter(frame -> frame.getClassName().startsWith("ti4.ai."))
                .filter(frame -> !frame.getClassName().startsWith(HiddenInfoGuardTest.class.getName()))
                .findFirst()
                .map(frame -> frame.getClassName()
                                .substring(frame.getClassName().lastIndexOf('.') + 1) + "." + frame.getMethodName())
                .orElse("outside the AI");
    }
}
